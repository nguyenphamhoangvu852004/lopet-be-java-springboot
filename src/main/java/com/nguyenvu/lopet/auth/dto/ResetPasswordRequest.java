package com.nguyenvu.lopet.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code email} bị ghép thẳng vào khoá Redis {@code email_verified:<email>} và dùng để tra tài
 * khoản, nên không nhận chuỗi tuỳ ý — ràng buộc {@code @Email} ở đây là bắt buộc chứ không phải cho đẹp.
 */
public record ResetPasswordRequest(

        @NotNull(message = "\"email\" is required")
        @NotBlank(message = "\"email\" is not allowed to be empty")
        @Email(message = "\"email\" must be a valid email")
        String email,

        @NotNull(message = "\"password\" is required")
        @NotBlank(message = "\"password\" is not allowed to be empty")
        @Size(min = 6, message = "\"password\" length must be at least 6 characters long")
        String password,

        @NotNull(message = "\"confirmPassword\" is required")
        @NotBlank(message = "\"confirmPassword\" is not allowed to be empty")
        @Size(min = 6, message = "\"confirmPassword\" length must be at least 6 characters long")
        String confirmPassword) {
}
