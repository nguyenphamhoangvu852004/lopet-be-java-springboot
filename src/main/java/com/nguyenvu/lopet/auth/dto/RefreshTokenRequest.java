package com.nguyenvu.lopet.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Thông điệp lỗi giữ đúng khuôn của Joi ({@code "\"field\" is required"}) như các DTO auth khác, vì
 * client hiển thị thẳng chuỗi trong mảng {@code errors}.
 */
public record RefreshTokenRequest(

        @NotNull(message = "\"refreshToken\" is required")
        @NotBlank(message = "\"refreshToken\" is not allowed to be empty")
        String refreshToken) {
}
