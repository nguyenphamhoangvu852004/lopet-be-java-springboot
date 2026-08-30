package com.nguyenvu.lopet.email.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VerifyOtpRequest(

        @NotNull(message = "\"email\" is required")
        @NotBlank(message = "\"email\" is not allowed to be empty")
        @Email(message = "\"email\" must be a valid email")
        String email,

        @NotNull(message = "\"otp\" is required")
        @NotBlank(message = "\"otp\" is not allowed to be empty")
        String otp) {
}
