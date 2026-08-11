package com.nguyenvu.lopet.comment;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.comment.dto.CommentDtos;
import com.nguyenvu.lopet.comment.entity.Comment;
import com.nguyenvu.lopet.comment.repository.CommentRepository;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.post.entity.Post;
import com.nguyenvu.lopet.post.repository.PostRepository;
import com.nguyenvu.lopet.profile.entity.Profile;
import com.nguyenvu.lopet.profile.repository.ProfileRepository;

import lombok.RequiredArgsConstructor;

/**
 * Bình luận thừa hưởng quyền riêng tư của BÀI VIẾT chứa nó — không có quyền riêng tư riêng.
 *
 * <p>Trước bản vá, luồng đọc bình luận nạp bài bằng hàm không lọc còn route thì không xác thực, nên
 * danh sách bình luận (kèm username/email người bình luận) của một bài PRIVATE vẫn đọc được bởi
 * khách vãng lai dù thân bài đã được che.
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;

    @Transactional
    public CommentDtos.CreateCommentResponse create(Integer accountId, Integer postId, Integer replyCommentId,
                                                     String content, String imageUrl) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BadRequestException("No account found"));

        // Không bình luận được vào bài mà mình không có quyền xem
        Post post = postRepository.findVisibleById(postId, accountId)
                .orElseThrow(() -> new BadRequestException("No post found"));

        Comment parent = null;
        if (replyCommentId != null) {
            Comment candidate = commentRepository.findDetailById(replyCommentId)
                    .orElseThrow(() -> new BadRequestException("No comment found"));
            // Bình luận cha PHẢI thuộc đúng bài vừa được kiểm quyền ở trên. Không đối chiếu thì chỉ
            // cần ghép postId của một bài mình xem được với replyCommentId lấy từ bài PRIVATE của
            // người khác là tạo được reply gắn vào cây bình luận đó — tức là kiểm nhầm tài nguyên.
            if (candidate.getPost() == null || !candidate.getPost().getId().equals(post.getId())) {
                throw new BadRequestException("No comment found");
            }
            parent = candidate;
        }

        Comment saved = commentRepository.save(Comment.builder()
                .images(imageUrl == null ? "" : imageUrl)
                .text(content)
                .account(account)
                .parent(parent)
                .post(post)
                .build());

        return new CommentDtos.CreateCommentResponse(saved.getId());
    }

    @Transactional(readOnly = true)
    public CommentDtos.GetCommentsResponse getAllFromPost(Integer postId, Integer viewerId) {
        Post post = postRepository.findVisibleById(postId, viewerId)
                .orElseThrow(() -> new BadRequestException("No post found"));

        List<Comment> comments = commentRepository.findAllByPostId(post.getId());

        // Bản TS gọi profileRepo theo TỪNG bình luận (N+1). Ở đây nạp một lượt rồi tra map —
        // response không đổi, chỉ số truy vấn đổi.
        List<Integer> accountIds = comments.stream().map(comment -> comment.getAccount().getId()).distinct().toList();
        Map<Integer, Profile> profiles = accountIds.isEmpty() ? Map.of()
                : accountIds.stream()
                        .map(profileRepository::findByAccountId)
                        .flatMap(java.util.Optional::stream)
                        .collect(Collectors.toMap(profile -> profile.getAccount().getId(), Function.identity(),
                                (first, second) -> first));

        List<CommentDtos.CommentItem> items = comments.stream()
                .map(comment -> toItem(comment, profiles.get(comment.getAccount().getId())))
                .toList();

        return new CommentDtos.GetCommentsResponse(post.getId(), items);
    }

    /**
     * Quyền sở hữu đã được kiểm ở tầng guard trước khi vào đây và cố ý KHÔNG lặp lại: guard cho
     * staff (quyền {@code post:delete}) bỏ qua ownership để kiểm duyệt, còn kiểm tra tại service lại
     * chặn đúng nhóm đó.
     */
    @Transactional
    public CommentDtos.DeleteCommentResponse delete(Integer commentId) {
        Comment comment = commentRepository.findDetailById(commentId)
                .orElseThrow(() -> new BadRequestException("No comment found"));
        commentRepository.delete(comment);
        return new CommentDtos.DeleteCommentResponse(comment.getId());
    }

    private CommentDtos.CommentItem toItem(Comment comment, Profile profile) {
        Account account = comment.getAccount();
        CommentDtos.CommentProfile profileDto = new CommentDtos.CommentProfile(
                profile == null ? 0 : profile.getId(),
                orEmpty(profile == null ? null : profile.getAvatarUrl()),
                orEmpty(profile == null ? null : profile.getCoverUrl()),
                orEmpty(profile == null ? null : profile.getBio()),
                orEmpty(profile == null ? null : profile.getFullName()),
                orEmpty(profile == null ? null : profile.getPhoneNumber()),
                profile == null || profile.getSex() == null ? 0 : profile.getSex(),
                profile == null || profile.getDateOfBirth() == null ? LocalDate.now() : profile.getDateOfBirth(),
                orEmpty(profile == null ? null : profile.getHometown()));

        CommentDtos.CommentAccount accountDto = new CommentDtos.CommentAccount(
                account.getId(), account.getUsername(), account.getEmail(), profileDto);

        return new CommentDtos.CommentItem(comment.getId(), accountDto,
                comment.getParent() == null ? null : comment.getParent().getId(),
                comment.getText(), comment.getImages(), comment.getCreatedAt());
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
