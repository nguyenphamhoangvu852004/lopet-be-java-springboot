package com.nguyenvu.lopet.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nguyenvu.lopet.auth.dto.LoginRequest;
import com.nguyenvu.lopet.auth.dto.LoginResponse;
import com.nguyenvu.lopet.auth.dto.RefreshTokenResponse;
import com.nguyenvu.lopet.auth.dto.RegisterRequest;
import com.nguyenvu.lopet.auth.dto.RegisterResponse;
import com.nguyenvu.lopet.auth.dto.ResetPasswordRequest;
import com.nguyenvu.lopet.auth.dto.ResetPasswordResponse;
import com.nguyenvu.lopet.auth.dto.VerifyAccountRequest;
import com.nguyenvu.lopet.auth.dto.VerifyAccountResponse;
import com.nguyenvu.lopet.common.response.ApiResponse;
import com.nguyenvu.lopet.common.response.HttpStatusMessage;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Authentication management", description = "APIs for managing user identity")
@RestController
@RequestMapping("")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookie refreshTokenCookie;

    @Operation(summary = "Login", description = "API allow user login into system. Refresh token is returned as an HttpOnly cookie, not in the response body")
    @PostMapping("/v1/auth/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletResponse response) {
        IssuedTokens tokens = authService.login(request);
        refreshTokenCookie.write(response, tokens.refreshToken());
        return ApiResponse.ok(HttpStatusMessage.OK, new LoginResponse(tokens.id(), tokens.accessToken()));
    }

    @Operation(summary = "Get new Access Token", description = "API used to create a new access token by using the refresh token cookie")
    @PostMapping("/v1/auth/refresh")
    public ApiResponse<RefreshTokenResponse> refresh(HttpServletRequest request,
                                                     HttpServletResponse response) {
        IssuedTokens tokens = authService.refresh(refreshTokenCookie.read(request));
        refreshTokenCookie.write(response, tokens.refreshToken());
        return ApiResponse.ok(HttpStatusMessage.OK, new RefreshTokenResponse(tokens.id(), tokens.accessToken()));
    }

    @Operation(summary = "Register new Account",description = "API create new account")
    @PostMapping("/v1/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.created(HttpStatusMessage.CREATED, authService.register(request));
    }

    @Operation(summary = "Verify the OTP sent to email", description = "API used before any behavior that need verify OTP. Verify the OTP for any next step required")
    @PostMapping("/v1/auth/verify")
    public ApiResponse<VerifyAccountResponse> verifyAccount(@RequestBody VerifyAccountRequest request) {
        return ApiResponse.ok(HttpStatusMessage.OK, authService.verifyAccount(request));
    }

    @Operation(summary = "Forget password", description = "API used to reset a forgot password. Need to be verify OTP before use this API")
    @PostMapping("/v1/password/reset")
    public ApiResponse<ResetPasswordResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ApiResponse.ok(HttpStatusMessage.OK, authService.resetPassword(request));
    }
}
