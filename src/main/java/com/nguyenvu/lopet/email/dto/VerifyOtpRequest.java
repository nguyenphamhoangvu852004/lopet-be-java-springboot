package com.nguyenvu.lopet.email.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Body của POST /v1/emails/verify: cặp email + mã OTP mà người dùng nhận được trong thư.
 *
 * <p>Không ràng buộc độ dài {@code otp} — mã sai độ dài vẫn phải rơi vào nhánh so sánh của
 * {@code EmailService} để client nhận đúng message "OTP không chính xác." thay vì lỗi validation.
 */
public record VerifyOtpRequest(

        @NotNull(message = "\"email\" is required")
        @NotBlank(message = "\"email\" is not allowed to be empty")
        @Email(message = "\"email\" must be a valid email")
        String email,

        @NotNull(message = "\"otp\" is required")
        @NotBlank(message = "\"otp\" is not allowed to be empty")
        String otp) {
}
