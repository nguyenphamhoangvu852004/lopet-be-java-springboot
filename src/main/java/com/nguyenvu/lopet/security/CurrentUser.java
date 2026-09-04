package com.nguyenvu.lopet.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nguyenvu.lopet.common.exception.UnauthorizedException;
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
            throw new UnauthorizedException("Authentication required");
        }
        return principal;
    }

    private CurrentUser() {
    }
}
