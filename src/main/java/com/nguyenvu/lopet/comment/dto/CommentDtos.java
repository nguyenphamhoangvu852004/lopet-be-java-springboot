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

    public record CommentItem(
            Integer id,
            CommentAccount account,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer replyToCommentId,
            String content,
            String imageUrl,
            LocalDateTime createdAt) {
    }

    public record CommentAccount(Integer id, String username, String email, CommentProfile profile) {
    }

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
