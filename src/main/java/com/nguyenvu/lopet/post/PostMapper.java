package com.nguyenvu.lopet.post;

import java.util.Comparator;
import java.util.List;

import com.nguyenvu.lopet.post.dto.PostDtos;
import com.nguyenvu.lopet.post.entity.Post;
import com.nguyenvu.lopet.post.entity.PostLike;
import com.nguyenvu.lopet.post.entity.PostMedia;

/** Chỉ được gọi bên trong transaction: các collection của Post đều lazy. */
public final class PostMapper {

    /**
     * {@code null} khi bài không quy được về tài khoản nào — cột {@code posts.account_id} là
     * NULLABLE và dữ liệu cũ có thể còn hàng rỗng.
     *
     * <p>KHÔNG ném ở đây: mapper chạy trên đường ĐỌC, và một hàng dữ liệu cũ không được phép
     * làm hỏng cả trang feed. Tầng trên nhận null và tự quyết cách hiển thị tác giả.
     */
    private static Integer accountIdOf(Post post) {
        return post.getAccount() == null ? null : post.getAccount().getId();
    }

    public static PostDtos.PostListItem toListItem(Post post) {
        return new PostDtos.PostListItem(
                post.getId(),
                accountIdOf(post),
                post.getContent(),
                post.getGroup() == null ? null : post.getGroup().getId(),
                post.getPostType(),
                mediasWithId(post),
                post.getPostLikes().size(),
                likedAccounts(post),
                post.getCreatedAt(),
                post.getUpdatedAt());
    }

    public static PostDtos.PostSuggestItem toSuggestItem(Post post) {
        return new PostDtos.PostSuggestItem(
                post.getId(),
                accountIdOf(post),
                post.getContent(),
                post.getGroup() == null ? null : post.getGroup().getId(),
                post.getPostType(),
                mediasWithoutId(post),
                post.getPostLikes().size(),
                post.getCreatedAt(),
                post.getUpdatedAt());
    }

    public static PostDtos.PostDetail toDetail(Post post) {
        return new PostDtos.PostDetail(
                post.getId(),
                accountIdOf(post),
                post.getContent(),
                post.getGroup() == null ? null : post.getGroup().getId(),
                post.getPostType(),
                mediasWithId(post),
                post.getPostLikes().size(),
                likedAccounts(post),
                post.getCreatedAt(),
                post.getUpdatedAt());
    }

    public static PostDtos.PostByAccountItem toByAccountItem(Post post) {
        return new PostDtos.PostByAccountItem(
                post.getId(),
                post.getContent(),
                post.getGroup() == null ? null : post.getGroup().getId(),
                post.getPostType(),
                mediasWithoutId(post),
                post.getPostLikes().size(),
                post.getCreatedAt(),
                post.getUpdatedAt());
    }

    public static PostDtos.MediaWithId toMediaWithId(PostMedia media) {
        return new PostDtos.MediaWithId(media.getId(), media.getMediaUrl(), media.getMediaType(),
                media.getCreatedAt(), media.getUpdatedAt());
    }

    private static List<PostDtos.MediaWithId> mediasWithId(Post post) {
        return sortedMedias(post).map(PostMapper::toMediaWithId).toList();
    }

    private static List<PostDtos.MediaWithoutId> mediasWithoutId(Post post) {
        return sortedMedias(post)
                .map(media -> new PostDtos.MediaWithoutId(media.getMediaUrl(), media.getMediaType(),
                        media.getCreatedAt(), media.getUpdatedAt()))
                .toList();
    }

    /** Set của Hibernate không giữ thứ tự — sắp theo id để danh sách media ổn định */
    private static java.util.stream.Stream<PostMedia> sortedMedias(Post post) {
        return post.getPostMedias().stream().sorted(Comparator.comparing(PostMedia::getId));
    }

    /**
     * Lượt thích mất tài khoản bị BỎ QUA thay vì trả về một phần tử rỗng: danh sách này chỉ
     * dùng để hiện ai đã thích, và một dòng không có danh tính thì không hiện được gì.
     * {@code likeAmount} vẫn đếm đủ vì nó lấy từ {@code postLikes.size()}.
     */
    private static List<PostDtos.LikedAccount> likedAccounts(Post post) {
        return post.getPostLikes().stream()
                .sorted(Comparator.comparing(PostLike::getId))
                .filter(like -> like.getAccount() != null)
                .map(like -> new PostDtos.LikedAccount(like.getAccount().getId(),
                        like.getAccount().getUsername(), like.getAccount().getEmail()))
                .toList();
    }

    private PostMapper() {
    }
}
