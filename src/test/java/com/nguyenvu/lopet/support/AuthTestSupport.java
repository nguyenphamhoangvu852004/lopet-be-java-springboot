package com.nguyenvu.lopet.support;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nguyenvu.lopet.security.jwt.UserPrincipal;

public final class AuthTestSupport {

    public static void actAs(Integer accountId) {
        UserPrincipal principal = new UserPrincipal(accountId, "test-" + accountId + "@test.local");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }

    private AuthTestSupport() {
    }
}
