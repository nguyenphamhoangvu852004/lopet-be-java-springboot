package com.nguyenvu.lopet.post;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.group.entity.Group;
import com.nguyenvu.lopet.notification.NotificationPublisher;
import com.nguyenvu.lopet.post.dto.PostDtos;
import com.nguyenvu.lopet.post.entity.MediaType;
import com.nguyenvu.lopet.post.entity.Post;
import com.nguyenvu.lopet.post.entity.PostLike;
import com.nguyenvu.lopet.post.entity.PostMedia;
import com.nguyenvu.lopet.post.repository.PostLikeRepository;
import com.nguyenvu.lopet.post.repository.PostMediaRepository;
import com.nguyenvu.lopet.post.repository.PostRepository;

import lombok.RequiredArgsConstructor;

/**
 * {@code viewerId} lấy từ token qua {@code @Auth(required = false)}; {@code null} = khách chưa đăng
 * nhập. Mọi luồng đọc bài đều phải truyền tham số này xuống repository để lọc quyền riêng tư —
 * lọc sau khi đã nạp hết bài về là sai, vì dữ liệu đã rời khỏi tầng có thẩm quyền.
 */
@Service
@RequiredArgsConstructor
public class PostService {

    /** Bản TS lấy đúng 10 bài mới nhất cho feed gợi ý */
    private static final int SUGGEST_SIZE = 10;

    private final PostRepository postRepository;
    private final PostMediaRepository postMediaRepository;
    private final PostLikeRepository postLikeRepository;
    private final NotificationPublisher notificationPublisher;
    private final AccountRepository accountRepository;
    private final PostPolicy postPolicy;

    @Transactional(readOnly = true)
    public List<PostDtos.PostSuggestItem> getSuggestList(Integer viewerId) {
        List<Integer> ids = postRepository.findVisibleIds(viewerId, PageRequest.of(0, SUGGEST_SIZE));
        if (ids.isEmpty()) {
            return List.of();
        }
        return postRepository.findAllByIdsWithDetails(ids).stream()
                .map(PostMapper::toSuggestItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PostDtos.PostListItem> getAll(String content, Integer groupId, Integer viewerId) {
        return postRepository.findAllVisible(viewerId, content, groupId).stream()
                .map(PostMapper::toListItem)
                .toList();
    }

    /**
     * Trả 404 (không phải 403) khi người xem không đủ quyền: phản hồi phải giống hệt trường hợp bài
     * không tồn tại, nếu không endpoint này trở thành công cụ dò xem một bài PRIVATE có tồn tại hay không.
     */
    @Transactional(readOnly = true)
    public PostDtos.PostDetail getOneById(Integer id, Integer viewerId) {
        Post post = postRepository.findVisibleById(id, viewerId).orElseThrow(NotFoundException::new);
        return PostMapper.toDetail(post);
    }

    @Transactional(readOnly = true)
    public List<PostDtos.PostByAccountItem> getByAccountId(Integer accountId, Integer viewerId) {
        return postRepository.findVisibleByAuthor(accountId, viewerId).stream()
                .map(PostMapper::toByAccountItem)
                .toList();
    }

    @Transactional
    public PostDtos.CreatePostResponse create(Integer accountId, String content, Integer groupId, String scope,
                                               List<UploadedMedia> medias) {
        Account account = accountRepository.findById(accountId).orElseThrow(BadRequestException::new);

        // Quyền đăng vào group do policy quyết, không phải controller: controller chỉ thấy body,
        // còn quyết định phụ thuộc group.type và tư cách thành viên.
        Group group = groupId == null ? null : postPolicy.resolveGroupForPost(groupId, account.getId());

        Post post = Post.builder()
                .account(account)
                .content(content)
                .group(group)
                .build();
        post.applyType();
        // Suy ra từ group đã nạp thật, không từ groupId trong body
        post.setPostScope(postPolicy.parseScope(scope, group != null));

        Post saved = postRepository.save(post);

        List<PostDtos.MediaWithId> savedMedias = new ArrayList<>();
        for (UploadedMedia media : medias) {
            PostMedia entity = postMediaRepository.save(PostMedia.builder()
                    .post(saved)
                    .mediaUrl(media.url())
                    .mediaType(media.type())
                    .build());
            savedMedias.add(PostMapper.toMediaWithId(entity));
        }

        // createdAt/updatedAt của response là thời điểm hiện tại, đúng như bản TS (new Date())
        LocalDateTime now = LocalDateTime.now();
        return new PostDtos.CreatePostResponse(saved.getAccount().getId(), saved.getId(),
                saved.getContent(), saved.getGroup() == null ? null : saved.getGroup().getId(),
                saved.getPostType(), saved.getPostScope(), savedMedias, now, now);
    }

    /**
     * {@code keepMediaIds} là danh sách media được GIỮ LẠI; phần còn lại của bài bị xoá.
     *
     * <p><b>{@code null} và danh sách rỗng KHÔNG giống nhau.</b> {@code null} = "request không nhắc
     * gì tới media" → giữ nguyên; rỗng = "giữ lại không cái nào" → xoá hết. Trước đây hai ca này bị
     * gộp làm một và cùng rơi vào nhánh xoá sạch, nên chỉ cần sửa mỗi nội dung mà quên gửi
     * {@code oldIdsMedia} là toàn bộ ảnh/video của bài biến mất — mất dữ liệu thật, im lặng, không
     * hoàn tác được.
     *
     * <p>Id không thuộc chính bài này bị từ chối thay vì bỏ qua: nhận nó thì response trả về media
     * của bài khác, mà media đó cũng chẳng được giữ lại thật.
     *
     * <p>Kiểm tra sở hữu ở đây là lớp thứ hai (lớp thứ nhất là ownership guard trên controller): bản
     * TS cũng kiểm hai lần, và lớp trong này chặn cả trường hợp service được gọi từ nơi khác.
     */
    @Transactional
    public PostDtos.UpdatePostResponse update(Integer postId, Integer callerId, String content, String scope,
                                               List<Integer> keepMediaIds, List<UploadedMedia> newMedias) {
        Post post = postRepository.findByIdInternal(postId)
                .orElseThrow(() -> new BadRequestException("Post not found"));

        // So bằng callerId ở vế trái: bài không có tác giả (account_id NULL) thì
        // ownerAccountIdOf trả null và không ai sửa được nó — đúng hơn là để NPE.
        if (!callerId.equals(ownerAccountIdOf(post))) {
            throw new ForbiddenException("You are not the owner of this post");
        }

        post.setContent(content);
        // Scope hợp lệ quyết theo group THẬT của bài (đã nạp từ DB), không theo groupId client gửi
        // lên — tin body thì chỉ cần bỏ trống groupId là đặt được scope FRIEND cho một bài trong nhóm.
        // Bài viết không đổi được group, nên đây cũng là group sau khi sửa.
        post.setPostScope(postPolicy.parseScope(scope, post.getGroup() != null));

        Post updated = postRepository.save(post);

        List<PostDtos.MediaWithId> result = new ArrayList<>();
        if (keepMediaIds == null) {
            postMediaRepository.findByPostId(postId).forEach(media -> result.add(PostMapper.toMediaWithId(media)));
        } else {
            Map<Integer, PostMedia> current = postMediaRepository.findByPostId(postId).stream()
                    .collect(Collectors.toMap(PostMedia::getId, media -> media));

            // Kiểm TRƯỚC khi xoá: một id sai thì dừng lại từ đầu thay vì xoá xong mới phát hiện và
            // dựa vào rollback để cứu.
            for (Integer id : keepMediaIds) {
                PostMedia media = current.get(id);
                if (media == null) {
                    throw new BadRequestException("Old media not found: ID " + id);
                }
                result.add(PostMapper.toMediaWithId(media));
            }

            if (keepMediaIds.isEmpty()) {
                postMediaRepository.deleteAllByPostId(postId);
            } else {
                postMediaRepository.deleteByPostIdAndIdNotIn(postId, keepMediaIds);
            }
            postMediaRepository.flush();
        }
        for (UploadedMedia media : newMedias) {
            PostMedia entity = postMediaRepository.save(PostMedia.builder()
                    .post(updated)
                    .mediaUrl(media.url())
                    .mediaType(media.type())
                    .build());
            result.add(PostMapper.toMediaWithId(entity));
        }

        return new PostDtos.UpdatePostResponse(ownerAccountIdOf(updated), updated.getId(),
                updated.getContent(), updated.getPostType(), updated.getPostScope(),
                updated.getGroup() == null ? null : updated.getGroup().getId(), result,
                updated.getCreatedAt(), LocalDateTime.now());
    }

    /** Xoá cứng — ownership đã được kiểm ở tầng guard trước khi vào đây */
    @Transactional
    public PostDtos.DeletePostResponse delete(Integer postId) {
        Post post = postRepository.findById(postId).orElseThrow(BadRequestException::new);
        postRepository.delete(post);
        return new PostDtos.DeletePostResponse(postId);
    }

    /**
     * Bài mình không có quyền xem thì cũng không thả tim được. Nạp bằng bản CÓ lọc quyền: nếu dùng
     * bản không lọc thì endpoint này thành công cụ dò — phản hồi khác nhau giữa "bài không tồn tại"
     * và "bài PRIVATE của người khác".
     */
    @Transactional
    public PostDtos.ReactResponse like(Integer postId, Integer accountId) {
        Post post = postRepository.findVisibleById(postId, accountId).orElseThrow(BadRequestException::new);
        Account account = accountRepository.findById(accountId).orElseThrow(BadRequestException::new);

        if (postLikeRepository.findByAccountAndPost(account.getId(), post.getId()).isPresent()) {
            // Idempotent: trả 200 kèm message chứ không phải lỗi
            return new PostDtos.ReactResponse("You have already liked this post");
        }

        postLikeRepository.save(PostLike.builder().post(post).account(account).build());

        // Sau nhánh idempotent phía trên: thích lại bài đã thích không sinh thêm thông báo nào.
        // Bỏ thích rồi thích lại thì có — đó là một lượt thích mới thật sự.
        notificationPublisher.postLiked(accountId, ownerAccountIdOf(post), post.getId());

        return new PostDtos.ReactResponse("Like post successfully");
    }

    @Transactional
    public PostDtos.ReactResponse unlike(Integer postId, Integer accountId) {
        Post post = postRepository.findVisibleById(postId, accountId).orElseThrow(BadRequestException::new);
        Account account = accountRepository.findById(accountId).orElseThrow(BadRequestException::new);

        PostLike existing = postLikeRepository.findByAccountAndPost(account.getId(), post.getId())
                .orElse(null);
        if (existing == null) {
            return new PostDtos.ReactResponse("You have already unliked this post");
        }

        postLikeRepository.delete(existing);
        return new PostDtos.ReactResponse("Unlike post successfully");
    }

    /** Tác giả bài; {@code null} với bài dữ liệu cũ không còn quy được về tài khoản nào */
    private Integer ownerAccountIdOf(Post post) {
        return post.getAccount() == null ? null : post.getAccount().getId();
    }

    /** Ảnh/video đã upload xong lên Cloudinary, chờ gắn vào bài */
    public record UploadedMedia(String url, MediaType type) {
    }
}
