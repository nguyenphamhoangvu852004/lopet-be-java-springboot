package com.nguyenvu.lopet.post.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.nguyenvu.lopet.post.entity.MediaType;
import com.nguyenvu.lopet.post.entity.PostScope;
import com.nguyenvu.lopet.post.entity.PostType;

/**
 * Bốn luồng đọc bài của lopet-be dựng bốn DTO KHÁC NHAU, và sự khác nhau không phải ngẫu nhiên mà
 * là hệ quả của việc mỗi hàm chỉ gán một tập trường:
 *
 * <ul>
 *   <li>{@code getAll} có {@code likeList} và media CÓ {@code id}</li>
 *   <li>{@code getSuggestList} KHÔNG có {@code likeList}, media KHÔNG có {@code id}</li>
 *   <li>{@code getOneById} có {@code listLike} (tên khác!), không có {@code commentAmount}/{@code shareAmount}
 *       dù DTO khai báo chúng — hai trường đó không bao giờ được gán</li>
 *   <li>{@code getByAccountId} KHÔNG có {@code accountId}, không có danh sách like, media không có id</li>
 * </ul>
 *
 * Trường nào bên TS không được gán thì khoá đó vắng mặt trong JSON, nên các record dưới đây khai
 * đúng bằng số trường thực sự xuất hiện.
 */
public final class PostDtos {

    /** Media kèm id — dùng cho getAll, getOneById, create, update */
    public record MediaWithId(
            Integer id,
            String mediaUrl,
            MediaType mediaType,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    /** Media không có id — dùng cho getSuggestList và getByAccountId */
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
            Integer groupId,
            PostType postType,
            List<MediaWithId> postMedias,
            Integer likeAmount,
            List<LikedAccount> likeList,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record PostSuggestItem(
            Integer postId,
            Integer accountId,
            String content,
            Integer groupId,
            PostType postType,
            List<MediaWithoutId> postMedias,
            Integer likeAmount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record PostDetail(
            Integer postId,
            Integer accountId,
            String content,
            Integer groupId,
            PostType postType,
            List<MediaWithId> postMedias,
            Integer likeAmount,
            List<LikedAccount> listLike,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record PostByAccountItem(
            Integer postId,
            String content,
            Integer groupId,
            PostType postType,
            List<MediaWithoutId> postMedias,
            Integer likeAmount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record CreatePostResponse(
            Integer accountId,
            Integer postId,
            String content,
            Integer groupId,
            PostType postType,
            PostScope scope,
            List<MediaWithId> postMedias,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record UpdatePostResponse(
            Integer owner,
            Integer postId,
            String content,
            PostType postType,
            PostScope scope,
            Integer groupId,
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

    /** Biến thể JSON (không kèm file) của POST /v1/posts */
    public record CreatePostRequest(String content, Integer groupId, String scope) {
    }

    /** Biến thể JSON của PUT /v1/posts/:postId */
    public record UpdatePostRequest(String content, String scope, List<Integer> oldIdsMedia) {
    }

    private PostDtos() {
    }
}
