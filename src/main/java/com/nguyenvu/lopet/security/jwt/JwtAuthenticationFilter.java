package com.nguyenvu.lopet.security.jwt;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Đọc token nếu có và nạp danh tính vào SecurityContext. Filter này KHÔNG tự từ chối request:
 * việc "route này có bắt buộc đăng nhập không" do {@link com.nguyenvu.lopet.security.AuthInterceptor}
 * quyết theo annotation {@link com.nguyenvu.lopet.security.Auth}, đúng như bên Express nơi
 * {@code verifyToken()} / {@code optionalAuth()} được gắn theo từng route chứ không toàn cục.
 *
 * <p>Kết quả giải mã được để lại ở request attribute để interceptor phân biệt ba trạng thái:
 * không có token / token hỏng / token hợp lệ — ba trạng thái này cho ba mã lỗi khác nhau.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ATTRIBUTE_STATE = "lopet.jwt.state";
    public static final String ATTRIBUTE_ERROR = "lopet.jwt.error";

    public enum TokenState {
        ABSENT, INVALID, VALID
    }

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extractToken(request);

        if (token == null) {
            request.setAttribute(ATTRIBUTE_STATE, TokenState.ABSENT);
            chain.doFilter(request, response);
            return;
        }

        try {
            UserPrincipal principal = jwtService.parseAccessToken(token);
            List<SimpleGrantedAuthority> authorities = principal.roles().stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, authorities));
            request.setAttribute(ATTRIBUTE_STATE, TokenState.VALID);
        } catch (JwtException exception) {
            request.setAttribute(ATTRIBUTE_STATE, TokenState.INVALID);
            request.setAttribute(ATTRIBUTE_ERROR, exception);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Giữ nguyên cách tách của TS: {@code req.header('Authorization')?.split(' ')[1]}. Header không
     * có khoảng trắng (thiếu tiền tố "Bearer") cho ra {@code undefined} — tức là coi như KHÔNG có
     * token, dẫn tới 400 "Token not found" chứ không phải 401.
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null) {
            return null;
        }
        String[] parts = header.split(" ");
        if (parts.length < 2 || parts[1].isEmpty()) {
            return null;
        }
        return parts[1];
    }
}
