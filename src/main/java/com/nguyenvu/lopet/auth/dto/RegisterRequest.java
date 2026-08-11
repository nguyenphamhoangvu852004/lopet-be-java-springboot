package com.nguyenvu.lopet.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotNull(message = "\"email\" is required")
        @NotBlank(message = "\"email\" is not allowed to be empty")
        @Email(message = "\"email\" must be a valid email")
        @Size(min = 12, message = "\"email\" length must be at least 12 characters long")
        String email,

        @NotNull(message = "\"username\" is required")
        @NotBlank(message = "\"username\" is not allowed to be empty")
        String username,

        @NotNull(message = "\"password\" is required")
        @NotBlank(message = "\"password\" is not allowed to be empty")
        @Size(min = 6, message = "\"password\" length must be at least 6 characters long")
        String password,

        @NotNull(message = "\"confirmPassword\" is required")
        @NotBlank(message = "\"confirmPassword\" is not allowed to be empty")
        @Size(min = 6, message = "\"confirmPassword\" length must be at least 6 characters long")
        String confirmPassword) {
}
