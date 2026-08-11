package com.nguyenvu.lopet.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Thông điệp lỗi được đặt trùng nguyên văn với Joi (xem {@code loginValidation}) vì client hiển thị
 * trực tiếp chuỗi trong mảng {@code errors}.
 */
public record LoginRequest(

        @NotNull(message = "\"username\" is required")
        @NotBlank(message = "\"username\" is not allowed to be empty")
        String username,

        @NotNull(message = "\"password\" is required")
        @NotBlank(message = "\"password\" is not allowed to be empty")
        @Size(min = 6, message = "\"password\" length must be at least 6 characters long")
        String password) {
}
