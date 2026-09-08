package com.nguyenvu.lopet.post.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.nguyenvu.lopet.post.entity.MediaType;

public final class PostDtos {

    public record MediaWithId(
            Integer id,
            String mediaUrl,
            MediaType mediaType,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record MediaWithoutId(
            String mediaUrl,
            MediaType mediaType,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record LikedAccount(Integer id, String username, String email) {
    }

    public record PostListItem(
            Integer postId,
            Integer accountId,
            String content,
            List<MediaWithId> postMedias,
            Integer likeAmount,
            List<LikedAccount> likeList,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record PostDetail(
            Integer postId,
            Integer accountId,
            String content,
            List<MediaWithId> postMedias,
            Integer likeAmount,
            List<LikedAccount> listLike,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record PostByAccountItem(
            Integer postId,
            String content,
            List<MediaWithoutId> postMedias,
            Integer likeAmount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record CreatePostResponse(
            Integer accountId,
            Integer postId,
            String content,
            List<MediaWithId> postMedias,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record UpdatePostResponse(
            Integer owner,
            Integer postId,
            String content,
            List<MediaWithId> postMedias,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record DeletePostResponse(Integer id) {
    }

    public record ReactRequest(Integer postId) {
    }

    public record ReactResponse(String message) {
    }

    private PostDtos() {
    }
}
