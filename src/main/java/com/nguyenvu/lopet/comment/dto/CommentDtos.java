package com.nguyenvu.lopet.comment.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public final class CommentDtos {

    public record CreateCommentResponse(Integer commentId) {
    }

    public record DeleteCommentResponse(Integer commentId) {
    }

    public record GetCommentsResponse(Integer postId, List<CommentItem> comments) {
    }

    /**
     * {@code replyToCommentId} vắng mặt khi đây là bình luận gốc — bản TS gán
     * {@code comment.parent?.id ?? undefined}, mà undefined thì JSON.stringify bỏ luôn khoá.
     */
    public record CommentItem(
            Integer id,
            CommentAccount account,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer replyToCommentId,
            String content,
            String imageUrl,
            LocalDateTime createdAt) {
    }

    /**
     * Cố ý KHÔNG có {@code roles}: bản TS dựng {@code GetAccountOutputDTO} chỉ với ba trường, nên
     * mảng roles không xuất hiện trong response của endpoint này.
     */
    public record CommentAccount(Integer id, String username, String email, CommentProfile profile) {
    }

    /**
     * Mọi trường đều có giá trị mặc định thay vì null — bản TS điền {@code ?? ''}, {@code ?? 0},
     * {@code ?? new Date()} cho tài khoản chưa có hồ sơ, và client cũ dựa vào việc các khoá này luôn
     * tồn tại.
     */
    public record CommentProfile(
            Integer id,
            String avatarUrl,
            String coverUrl,
            String bio,
            String fullName,
            String phoneNumber,
            Integer sex,
            LocalDate dateOfBirth,
            String hometown) {
    }

    private CommentDtos() {
    }
}
