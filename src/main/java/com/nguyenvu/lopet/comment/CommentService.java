package com.nguyenvu.lopet.comment;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.comment.dto.CommentDtos;
import com.nguyenvu.lopet.comment.entity.Comment;
import com.nguyenvu.lopet.comment.repository.CommentRepository;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.notification.NotificationPublisher;
import com.nguyenvu.lopet.pet.entity.Pet;
import com.nguyenvu.lopet.pet.repository.PetRepository;
import com.nguyenvu.lopet.petprofile.entity.PetProfile;
import com.nguyenvu.lopet.post.entity.Post;
import com.nguyenvu.lopet.post.repository.PostRepository;
import com.nguyenvu.lopet.security.petcontext.PetContext;

import lombok.RequiredArgsConstructor;

/**
 * Bình luận thừa hưởng quyền riêng tư của BÀI VIẾT chứa nó — không có quyền riêng tư riêng.
 *
 * <p>Trước bản vá, luồng đọc bình luận nạp bài bằng hàm không lọc còn route thì không xác thực, nên
 * danh sách bình luận (kèm username/email người bình luận) của một bài PRIVATE vẫn đọc được bởi
 * khách vãng lai dù thân bài đã được che.
 *
 * <p>Tác giả bình luận là một THÚ CƯNG, luôn lấy từ {@link PetContext#require()}. Tài khoản trong
 * token chỉ còn hai việc: lọc quyền xem bài, và làm người gửi/nhận của thông báo — hộp thông báo
 * thuộc về con người chứ không về con vật.
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final NotificationPublisher notificationPublisher;
    private final PetRepository petRepository;

    @Transactional
    public CommentDtos.CreateCommentResponse create(Integer accountId, Integer postId, Integer replyCommentId,
                                                     String content, String imageUrl) {
        Pet author = requireAuthorPet();

        // Không bình luận được vào bài mà mình không có quyền xem
        Post post = postRepository.findVisibleById(postId, accountId, author.getId())
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
                .pet(author)
                .parent(parent)
                .post(post)
                .build());

        notificationPublisher.postCommented(accountId, ownerAccountIdOf(post), post.getId());

        return new CommentDtos.CreateCommentResponse(saved.getId());
    }

    @Transactional(readOnly = true)
    public CommentDtos.GetCommentsResponse getAllFromPost(Integer postId, Integer viewerId, Integer viewerPetId) {
        Post post = postRepository.findVisibleById(postId, viewerId, viewerPetId)
                .orElseThrow(() -> new BadRequestException("No post found"));

        // Bản TS gọi profileRepo theo TỪNG bình luận (N+1). Hồ sơ công khai nay nằm trong đồ thị nạp
        // của findAllByPostId, nên không còn truy vấn phụ nào — response không đổi.
        List<CommentDtos.CommentItem> items = commentRepository.findAllByPostId(post.getId()).stream()
                .map(this::toItem)
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

    private CommentDtos.CommentItem toItem(Comment comment) {
        Pet pet = comment.getPet();
        PetProfile profile = pet == null ? null : pet.getPetProfile();

        CommentDtos.CommentProfile profileDto = new CommentDtos.CommentProfile(
                profile == null ? 0 : profile.getId(),
                orEmpty(profile == null ? null : profile.getHandle()),
                orEmpty(profile == null ? null : profile.getDisplayName()),
                orEmpty(profile == null ? null : profile.getAvatarUrl()),
                orEmpty(profile == null ? null : profile.getCoverUrl()),
                orEmpty(profile == null ? null : profile.getBio()));

        CommentDtos.CommentPet petDto = new CommentDtos.CommentPet(
                pet == null ? 0 : pet.getId(), orEmpty(pet == null ? null : pet.getName()), profileDto);

        return new CommentDtos.CommentItem(comment.getId(), petDto,
                comment.getParent() == null ? null : comment.getParent().getId(),
                comment.getText(), comment.getImages(), comment.getCreatedAt());
    }

    /** Xem ghi chú cùng tên ở {@code PostService} */
    private Pet requireAuthorPet() {
        Integer petId = PetContext.require();
        return petRepository.findById(petId)
                .orElseThrow(() -> new BadRequestException("Thú cưng không tồn tại hoặc đã ngừng hoạt động"));
    }

    /** Chủ tài khoản đứng sau pet tác giả của bài — người nhận thông báo */
    private Integer ownerAccountIdOf(Post post) {
        return post.getPet() == null || post.getPet().getAccount() == null
                ? null
                : post.getPet().getAccount().getId();
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
