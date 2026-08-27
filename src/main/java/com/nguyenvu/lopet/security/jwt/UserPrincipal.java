package com.nguyenvu.lopet.security.jwt;

import java.util.List;

public record UserPrincipal(Integer id, String email, List<String> roles) {

    public UserPrincipal {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
