package com.nguyenvu.lopet.post.entity;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import com.nguyenvu.lopet.account.entity.Account;

/**
 * Dựng Post ở những trạng thái mà API domain cố tình không cho phép (không tác
 * giả, media đã có id sẵn như vừa load từ DB). Đặt trong package {@code entity}
 * để chạm được builder package-scope, và chỉ tồn tại trong test.
 */
public final class PostFixtures {

    public static Post authorless(Integer id, String content, PostLike... likes) {
        return build(id, null, content, likes);
    }

    public static Post withAuthor(Integer id, Account author, String content, PostLike... likes) {
        return build(id, author, content, likes);
    }

    /** Một bài viết đã lưu, media mang sẵn id — dùng để test keepOnlyMedia. */
    public static Post withMedia(Integer id, Account author, String content, Integer... mediaIds) {
        Set<PostMedia> medias = new LinkedHashSet<>();
        Post post = Post.builder()
                .id(id)
                .account(author)
                .content(content)
                .postMedias(medias)
                .postLikes(new LinkedHashSet<>())
                .build();
        for (Integer mediaId : mediaIds) {
            medias.add(PostMedia.builder()
                    .id(mediaId)
                    .post(post)
                    .mediaUrl("https://cdn.test.local/" + mediaId)
                    .mediaType(MediaType.IMAGE)
                    .build());
        }
        return post;
    }

    public static PostLike like(Integer id, Account account) {
        return PostLike.builder().id(id).account(account).build();
    }

    private static Post build(Integer id, Account author, String content, PostLike... likes) {
        return Post.builder()
                .id(id)
                .account(author)
                .content(content)
                .postMedias(new LinkedHashSet<>())
                .postLikes(new LinkedHashSet<>(Arrays.asList(likes)))
                .build();
    }

    private PostFixtures() {
    }
}
