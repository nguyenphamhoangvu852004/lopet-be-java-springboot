package com.nguyenvu.lopet.security;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.RawJwtException;
import com.nguyenvu.lopet.common.exception.UnauthorizedException;
import com.nguyenvu.lopet.security.jwt.JwtAuthenticationFilter;
import com.nguyenvu.lopet.security.jwt.JwtAuthenticationFilter.TokenState;
import com.nguyenvu.lopet.security.jwt.JwtException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Áp đúng ngữ nghĩa hai middleware xác thực của lopet-be, dựa trên {@link Auth} gắn ở handler.
 *
 * <p>Chạy TRƯỚC khi vào controller nên cũng chạy trước aspect của {@link RequirePermission} —
 * giống thứ tự {@code verifyToken()} rồi mới tới {@code requirePermission()} trong file route.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        Auth auth = handlerMethod.getMethodAnnotation(Auth.class);
        if (auth == null) {
            auth = handlerMethod.getBeanType().getAnnotation(Auth.class);
        }
        if (auth == null) {
            return true;
        }

        TokenState state = (TokenState) request.getAttribute(JwtAuthenticationFilter.ATTRIBUTE_STATE);
        if (state == null) {
            state = TokenState.ABSENT;
        }

        if (auth.required()) {
            if (state == TokenState.ABSENT) {
                throw new BadRequestException("Token not found");
            }
            if (state == TokenState.INVALID) {
                throw new RawJwtException(reasonOf(request));
            }
            return true;
        }

        if (state == TokenState.INVALID) {
            throw new UnauthorizedException("Token không hợp lệ hoặc đã hết hạn");
        }
        return true;
    }

    private String reasonOf(HttpServletRequest request) {
        Object error = request.getAttribute(JwtAuthenticationFilter.ATTRIBUTE_ERROR);
        return error instanceof JwtException exception ? exception.getMessage() : JwtException.MALFORMED;
    }
}
