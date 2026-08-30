package com.nguyenvu.lopet.realtime;

import java.security.Principal;

import com.nguyenvu.lopet.security.jwt.UserPrincipal;

public record WebSocketPrincipal(UserPrincipal user) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(user.id());
    }

    public Integer accountId() {
        return user.id();
    }

    public static Integer accountIdOf(Principal principal) {
        return principal instanceof WebSocketPrincipal ws ? ws.accountId() : null;
    }
}
