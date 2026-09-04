package com.nguyenvu.lopet.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RefreshTokenCookie {

    private final String name;
    private final String path;
    private final boolean secure;
    private final String sameSite;
    private final int maxAgeSeconds;

    public RefreshTokenCookie(@Value("${lopet.auth.refresh-cookie.name}") String name,
                              @Value("${lopet.auth.refresh-cookie.path}") String path,
                              @Value("${lopet.auth.refresh-cookie.secure}") boolean secure,
                              @Value("${lopet.auth.refresh-cookie.same-site}") String sameSite,
                              @Value("${lopet.jwt.refresh-token-expires-in}") long refreshTtlSeconds) {
        this.name = name;
        this.path = path;
        this.secure = secure;
        this.sameSite = sameSite;
        this.maxAgeSeconds = (int) refreshTtlSeconds;
    }

    public String getName() {
        return name;
    }

    public void write(HttpServletResponse response, String refreshToken) {
        response.addCookie(build(refreshToken, maxAgeSeconds));
    }

    public void clear(HttpServletResponse response) {
        response.addCookie(build("", 0));
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                String value = cookie.getValue();
                return value == null || value.isBlank() ? null : value;
            }
        }
        return null;
    }

    private Cookie build(String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath(path);
        cookie.setMaxAge(maxAge);
        if (sameSite != null && !sameSite.isBlank()) {
            cookie.setAttribute("SameSite", sameSite);
        }
        return cookie;
    }
}
