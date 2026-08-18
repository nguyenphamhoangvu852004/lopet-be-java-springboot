package com.nguyenvu.lopet.comment.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public final class CommentDtos {

    public record CreateCommentRequest(String content, String postId, String replyCommentId) {
    }

    public record CreateCommentResponse(Integer commentId) {
    }

    public record DeleteCommentResponse(Integer commentId) {
    }

    public record GetCommentsResponse(Integer postId, List<CommentItem> comments) {
    }

    /**
     * {@code replyToCommentId} vắng mặt khi đây là bình luận gốc — bản TS gán
     * {@code comment.parent?.id ?? undefined}, mà undefined thì JSON.stringify bỏ luôn khoá.
     *
     * <p>Khoá {@code account} đã đổi thành {@code pet}: tác giả bình luận nay là một thú cưng.
     */
    public record CommentItem(
            Integer id,
            CommentPet pet,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer replyToCommentId,
            String content,
            String imageUrl,
            LocalDateTime createdAt) {
    }

    /**
     * Tác giả bình luận. Cố ý KHÔNG có username/email của chủ sở hữu: danh sách bình luận là nơi ai
     * cũng đọc được, còn tài khoản đứng sau con vật thì không phải thứ để lộ ra ở đó. Cũng không có
     * {@code roles} — bản TS dựng DTO chỉ với vài trường nhận diện.
     *
     * <p>{@code name} là tên thật của con vật ({@code pets.name}), khác {@code profile.displayName}
     * là tên hiện ra ngoài — xem {@link com.nguyenvu.lopet.pet.entity.Pet}.
     */
    public record CommentPet(Integer id, String name, CommentProfile profile) {
    }

    /**
     * Hồ sơ CÔNG KHAI của thú cưng, lấy từ {@code pet_profiles} chứ không còn từ hồ sơ tài khoản.
     *
     * <p>Mọi trường đều có giá trị mặc định thay vì null — bản TS điền {@code ?? ''}, {@code ?? 0}
     * cho tài khoản chưa có hồ sơ, và client cũ dựa vào việc các khoá này luôn tồn tại. Giữ nguyên
     * giao kèo đó cho trường hợp hồ sơ đã bị ngừng hoạt động: hàng bình luận vẫn còn, nhưng
     * {@code @SQLRestriction} trên {@code PetProfile} loại hồ sơ khỏi kết quả nạp.
     */
    public record CommentProfile(
            Integer id,
            String handle,
            String displayName,
            String avatarUrl,
            String coverUrl,
            String bio) {
    }

    private CommentDtos() {
    }
}
