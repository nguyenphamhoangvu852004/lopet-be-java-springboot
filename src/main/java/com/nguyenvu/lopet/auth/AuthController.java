package com.nguyenvu.lopet.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nguyenvu.lopet.auth.dto.LoginRequest;
import com.nguyenvu.lopet.auth.dto.LoginResponse;
import com.nguyenvu.lopet.auth.dto.RegisterRequest;
import com.nguyenvu.lopet.auth.dto.RegisterResponse;
import com.nguyenvu.lopet.auth.dto.ResetPasswordRequest;
import com.nguyenvu.lopet.auth.dto.ResetPasswordResponse;
import com.nguyenvu.lopet.auth.dto.VerifyAccountRequest;
import com.nguyenvu.lopet.auth.dto.VerifyAccountResponse;
import com.nguyenvu.lopet.common.response.ApiResponse;
import com.nguyenvu.lopet.common.response.HttpStatusMessage;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Bên TS cùng một router được mount HAI lần: {@code router.use('/auth', authRouter)} và
 * {@code router.use('/password', authRouter)}. Cả bốn endpoint vì thế tồn tại dưới cả hai tiền tố,
 * và client hiện tại đang gọi cả hai — nên {@code @RequestMapping} phải liệt kê đủ.
 */
@RestController
@RequestMapping({"/v1/auth", "/v1/password"})
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(HttpStatusMessage.OK, authService.login(request));
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.created(HttpStatusMessage.CREATED, authService.register(request));
    }

    @PostMapping("/verify")
    public ApiResponse<VerifyAccountResponse> verifyAccount(@RequestBody VerifyAccountRequest request) {
        return ApiResponse.ok(HttpStatusMessage.OK, authService.verifyAccount(request));
    }

    @PostMapping("/reset")
    public ApiResponse<ResetPasswordResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ApiResponse.ok(HttpStatusMessage.OK, authService.resetPassword(request));
    }
}
