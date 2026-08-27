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
