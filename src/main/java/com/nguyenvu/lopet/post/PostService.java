package com.nguyenvu.lopet.post;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.group.entity.Group;
import com.nguyenvu.lopet.notification.NotificationPublisher;
import com.nguyenvu.lopet.pet.entity.Pet;
import com.nguyenvu.lopet.pet.repository.PetRepository;
import com.nguyenvu.lopet.post.dto.PostDtos;
import com.nguyenvu.lopet.post.entity.MediaType;
import com.nguyenvu.lopet.post.entity.Post;
import com.nguyenvu.lopet.post.entity.PostLike;
import com.nguyenvu.lopet.post.entity.PostMedia;
import com.nguyenvu.lopet.post.repository.PostLikeRepository;
import com.nguyenvu.lopet.post.repository.PostMediaRepository;
import com.nguyenvu.lopet.post.repository.PostRepository;
import com.nguyenvu.lopet.security.petcontext.PetContext;

import lombok.RequiredArgsConstructor;

/**
 * Hai loại danh tính đi cùng nhau qua toàn bộ module này và không được thay thế cho nhau:
 *
 * <ul>
 *   <li><b>Tác giả</b> là một THÚ CƯNG, luôn lấy từ {@link PetContext#require()} — không bao giờ từ
 *       body, path param, hay suy ra từ tài khoản. Đây là lý do các hàm ghi dưới đây không nhận
 *       tham số petId: nhận vào là mở đường cho client tự khai mình là con khác.</li>
 *   <li><b>Quyền sở hữu</b> (sửa/xoá) vẫn xét ở mức TÀI KHOẢN: một người đổi qua lại giữa các thú
 *       cưng của mình và không được mất quyền lên nội dung mình đã đăng chỉ vì đang chọn con khác.
 *       {@code callerId} vì thế là id tài khoản trong token.</li>
 * </ul>
 *
 * <p>{@code viewerId}/{@code viewerPetId} lấy từ token và header {@code X-Pet-Id}; cả hai đều có
 * thể {@code null} (khách chưa đăng nhập, hoặc đã đăng nhập mà route đọc không đòi chọn pet). Mọi
 * luồng đọc bài đều phải truyền chúng xuống repository để lọc quyền riêng tư — lọc sau khi đã nạp
 * hết bài về là sai, vì dữ liệu đã rời khỏi tầng có thẩm quyền.
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
    private final PetRepository petRepository;
    private final PostPolicy postPolicy;

    @Transactional(readOnly = true)
    public List<PostDtos.PostSuggestItem> getSuggestList(Integer viewerId, Integer viewerPetId) {
        List<Integer> ids = postRepository.findVisibleIds(viewerId, viewerPetId,
                PageRequest.of(0, SUGGEST_SIZE));
        if (ids.isEmpty()) {
            return List.of();
        }
        return postRepository.findAllByIdsWithDetails(ids).stream()
                .map(PostMapper::toSuggestItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PostDtos.PostListItem> getAll(String content, Integer groupId, Integer viewerId,
                                              Integer viewerPetId) {
        return postRepository.findAllVisible(viewerId, viewerPetId, content, groupId).stream()
                .map(PostMapper::toListItem)
                .toList();
    }

    /**
     * Trả 404 (không phải 403) khi người xem không đủ quyền: phản hồi phải giống hệt trường hợp bài
     * không tồn tại, nếu không endpoint này trở thành công cụ dò xem một bài PRIVATE có tồn tại hay không.
     */
    @Transactional(readOnly = true)
    public PostDtos.PostDetail getOneById(Integer id, Integer viewerId, Integer viewerPetId) {
        Post post = postRepository.findVisibleById(id, viewerId, viewerPetId)
                .orElseThrow(NotFoundException::new);
        return PostMapper.toDetail(post);
    }

    /** Bài của MỘT thú cưng — đơn vị tác giả thật */
    @Transactional(readOnly = true)
    public List<PostDtos.PostByAccountItem> getByPetId(Integer petId, Integer viewerId, Integer viewerPetId) {
        return postRepository.findVisibleByAuthorPet(petId, viewerId, viewerPetId).stream()
                .map(PostMapper::toByAccountItem)
                .toList();
    }

    /** Bài của mọi thú cưng thuộc một tài khoản — giữ ý nghĩa cho route cũ theo accountId */
    @Transactional(readOnly = true)
    public List<PostDtos.PostByAccountItem> getByAccountId(Integer accountId, Integer viewerId,
                                                            Integer viewerPetId) {
        return postRepository.findVisibleByAuthorAccount(accountId, viewerId, viewerPetId).stream()
                .map(PostMapper::toByAccountItem)
                .toList();
    }

    @Transactional
    public PostDtos.CreatePostResponse create(String content, Integer groupId, String scope,
                                               List<UploadedMedia> medias) {
        Pet author = requireAuthorPet();

        // Quyền đăng vào group do policy quyết, không phải controller: controller chỉ thấy body,
        // còn quyết định phụ thuộc group.type và tư cách thành viên CỦA PET đang đăng.
        Group group = groupId == null ? null : postPolicy.resolveGroupForPost(groupId, author.getId());

        Post post = Post.builder()
                .pet(author)
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
        return new PostDtos.CreatePostResponse(saved.getPet().getId(), saved.getId(),
                saved.getContent(), saved.getGroup() == null ? null : saved.getGroup().getId(),
                saved.getPostType(), saved.getPostScope(), savedMedias, now, now);
    }

    /**
     * {@code oldIdsMedia} là danh sách media được GIỮ LẠI; phần còn lại của bài bị xoá.
     *
     * <p>Kiểm tra sở hữu ở đây là lớp thứ hai (lớp thứ nhất là ownership guard trên controller): bản
     * TS cũng kiểm hai lần, và lớp trong này chặn cả trường hợp service được gọi từ nơi khác.
     */
    @Transactional
    public PostDtos.UpdatePostResponse update(Integer postId, Integer callerId, String content, String scope,
                                               List<Integer> keepMediaIds, List<UploadedMedia> newMedias) {
        Post post = postRepository.findByIdInternal(postId)
                .orElseThrow(() -> new BadRequestException("Post not found"));

        if (!callerId.equals(ownerAccountIdOf(post))) {
            throw new ForbiddenException("You are not the owner of this post");
        }

        post.setContent(content);
        // Scope hợp lệ quyết theo group THẬT của bài (đã nạp từ DB), không theo groupId client gửi
        // lên — tin body thì chỉ cần bỏ trống groupId là đặt được scope FRIEND cho một bài trong nhóm.
        // Bài viết không đổi được group, nên đây cũng là group sau khi sửa.
        post.setPostScope(postPolicy.parseScope(scope, post.getGroup() != null));

        Post updated = postRepository.save(post);

        List<Integer> keep = keepMediaIds == null ? List.of() : keepMediaIds;
        if (keep.isEmpty()) {
            postMediaRepository.deleteAllByPostId(postId);
        } else {
            postMediaRepository.deleteByPostIdAndIdNotIn(postId, keep);
        }
        postMediaRepository.flush();

        List<PostDtos.MediaWithId> result = new ArrayList<>();
        for (Integer id : keep) {
            PostMedia media = postMediaRepository.findById(id)
                    .orElseThrow(() -> new BadRequestException("Old media not found: ID " + id));
            result.add(PostMapper.toMediaWithId(media));
        }
        for (UploadedMedia media : newMedias) {
            PostMedia entity = postMediaRepository.save(PostMedia.builder()
                    .post(updated)
                    .mediaUrl(media.url())
                    .mediaType(media.type())
                    .build());
            result.add(PostMapper.toMediaWithId(entity));
        }

        return new PostDtos.UpdatePostResponse(
                updated.getPet() == null ? null : updated.getPet().getId(), updated.getId(),
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
     *
     * <p>Lượt thích thuộc về PET, còn thông báo gửi cho NGƯỜI: {@code actorId}/{@code receptorId}
     * của {@link NotificationPublisher} vẫn là id tài khoản, vì hộp thông báo là của chủ chứ không
     * của con vật.
     */
    @Transactional
    public PostDtos.ReactResponse like(Integer postId, Integer accountId) {
        Pet liker = requireAuthorPet();
        Post post = postRepository.findVisibleById(postId, accountId, liker.getId())
                .orElseThrow(BadRequestException::new);

        if (postLikeRepository.findByPetAndPost(liker.getId(), post.getId()).isPresent()) {
            // Idempotent: trả 200 kèm message chứ không phải lỗi
            return new PostDtos.ReactResponse("You have already liked this post");
        }

        postLikeRepository.save(PostLike.builder().post(post).pet(liker).build());

        // Sau nhánh idempotent phía trên: thích lại bài đã thích không sinh thêm thông báo nào.
        // Bỏ thích rồi thích lại thì có — đó là một lượt thích mới thật sự.
        notificationPublisher.postLiked(accountId, ownerAccountIdOf(post), post.getId());

        return new PostDtos.ReactResponse("Like post successfully");
    }

    @Transactional
    public PostDtos.ReactResponse unlike(Integer postId, Integer accountId) {
        Pet liker = requireAuthorPet();
        Post post = postRepository.findVisibleById(postId, accountId, liker.getId())
                .orElseThrow(BadRequestException::new);

        PostLike existing = postLikeRepository.findByPetAndPost(liker.getId(), post.getId())
                .orElse(null);
        if (existing == null) {
            return new PostDtos.ReactResponse("You have already unliked this post");
        }

        postLikeRepository.delete(existing);
        return new PostDtos.ReactResponse("Unlike post successfully");
    }

    /**
     * Pet đang thao tác, đã được {@code PetContextInterceptor} xác nhận thuộc tài khoản trong token.
     * Nạp lại từ DB chứ không dùng {@code getReferenceById}: pet có thể vừa bị ngừng hoạt động sau
     * khi cache quyền sở hữu được ghi, và {@code @SQLRestriction} trên {@code Pet} là chỗ duy nhất
     * bắt được điều đó.
     */
    private Pet requireAuthorPet() {
        Integer petId = PetContext.require();
        return petRepository.findById(petId)
                .orElseThrow(() -> new BadRequestException("Thú cưng không tồn tại hoặc đã ngừng hoạt động"));
    }

    /** Chủ tài khoản đứng sau pet tác giả; {@code null} với bài chưa di trú xong */
    private Integer ownerAccountIdOf(Post post) {
        return post.getPet() == null || post.getPet().getAccount() == null
                ? null
                : post.getPet().getAccount().getId();
    }

    /** Ảnh/video đã upload xong lên Cloudinary, chờ gắn vào bài */
    public record UploadedMedia(String url, MediaType type) {
    }
}
