package com.nguyenvu.lopet.post;

import java.util.Comparator;
import java.util.List;

import com.nguyenvu.lopet.post.dto.PostDtos;
import com.nguyenvu.lopet.post.entity.Post;
import com.nguyenvu.lopet.post.entity.PostLike;
import com.nguyenvu.lopet.post.entity.PostMedia;

public final class PostMapper {

    public static PostDtos.PostListItem toListItem(Post post) {
        return new PostDtos.PostListItem(
                post.getId(),
                post.authorId(),
                post.getContent(),
                toMediasWithId(post),
                post.likeCount(),
                likedAccounts(post),
                post.getCreatedAt(),
                post.getUpdatedAt());
    }

    public static PostDtos.PostDetail toDetail(Post post) {
        return new PostDtos.PostDetail(
                post.getId(),
                post.authorId(),
                post.getContent(),
                toMediasWithId(post),
                post.likeCount(),
                likedAccounts(post),
                post.getCreatedAt(),
                post.getUpdatedAt());
    }

    public static PostDtos.PostByAccountItem toByAccountItem(Post post) {
        return new PostDtos.PostByAccountItem(
                post.getId(),
                post.getContent(),
                mediasWithoutId(post),
                post.likeCount(),
                post.getCreatedAt(),
                post.getUpdatedAt());
    }

    public static PostDtos.MediaWithId toMediaWithId(PostMedia media) {
        return new PostDtos.MediaWithId(media.getId(), media.getMediaUrl(), media.getMediaType(),
                media.getCreatedAt(), media.getUpdatedAt());
    }

    public static List<PostDtos.MediaWithId> toMediasWithId(Post post) {
        return post.mediasSorted().stream().map(PostMapper::toMediaWithId).toList();
    }

    private static List<PostDtos.MediaWithoutId> mediasWithoutId(Post post) {
        return post.mediasSorted().stream()
                .map(media -> new PostDtos.MediaWithoutId(media.getMediaUrl(), media.getMediaType(),
                        media.getCreatedAt(), media.getUpdatedAt()))
                .toList();
    }

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
