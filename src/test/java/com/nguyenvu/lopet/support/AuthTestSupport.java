package com.nguyenvu.lopet.support;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nguyenvu.lopet.security.jwt.UserPrincipal;

public final class AuthTestSupport {

    public static void actAs(Integer accountId) {
        actAs(accountId, List.of());
    }

    public static void actAs(Integer accountId, List<String> roles) {
        UserPrincipal principal = new UserPrincipal(accountId, "test-" + accountId + "@test.local", roles);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList()));
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }

    private AuthTestSupport() {
    }
}
