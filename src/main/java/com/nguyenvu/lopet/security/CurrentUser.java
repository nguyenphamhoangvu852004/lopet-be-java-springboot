package com.nguyenvu.lopet.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.security.jwt.UserPrincipal;

public final class CurrentUser {

    public static UserPrincipal optional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return null;
        }
        return principal;
    }

    public static UserPrincipal require() {
        UserPrincipal principal = optional();
        if (principal == null) {
            throw new ForbiddenException("Chưa xác thực");
        }
        return principal;
    }

    public static Integer viewerId() {
        UserPrincipal principal = optional();
        return principal == null ? null : principal.id();
    }

    private CurrentUser() {
    }
}
