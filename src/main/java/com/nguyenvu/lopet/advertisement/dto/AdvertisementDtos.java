package com.nguyenvu.lopet.advertisement.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

public final class AdvertisementDtos {

    /**
     * {@code linkReferfence} viết sai chính tả trong DTO gốc và client đang đọc đúng khoá đó — sửa
     * lại là làm hỏng hiển thị quảng cáo.
     *
     * <p>{@code updatedAt}/{@code deletedAt} vắng mặt khi null ({@code ?? undefined} bên TS).
     */
    public record AdvertisementDetail(
            Integer id,
            Author author,
            String title,
            String description,
            String imageUrl,
            String linkReferfence,
            LocalDateTime createdAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) LocalDateTime updatedAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) LocalDateTime deletedAt) {
    }

    public record Author(Integer id, String username, String email) {
    }

    public record CreateAdvertisementRequest(String title, String description, String linkRef) {
    }

    public record IdResponse(Integer id) {
    }

    public record DeleteResponse(String message) {
    }

    private AdvertisementDtos() {
    }
}
