package com.nguyenvu.lopet.email.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Body của POST /v1/emails.
 *
 * <p>{@code email} vừa là địa chỉ nhận thư vừa bị ghép thẳng vào khoá Redis {@code otp:<email>},
 * nên ràng buộc {@code @Email} ở đây chặn cả chuỗi rác lẫn khoá Redis dị dạng.
 */
public record SendOtpRequest(

        @NotNull(message = "\"email\" is required")
        @NotBlank(message = "\"email\" is not allowed to be empty")
        @Email(message = "\"email\" must be a valid email")
        String email) {
}
