package com.nguyenvu.lopet.security.jwt;

import lombok.Getter;

@Getter
public class JwtException extends RuntimeException {

    public static final String EXPIRED = "jwt expired";
    public static final String INVALID_SIGNATURE = "invalid signature";
    public static final String MALFORMED = "jwt malformed";

    private final boolean expired;

    public JwtException(String reason) {
        this(reason, false);
    }

    public JwtException(String reason, boolean expired) {
        super(reason);
        this.expired = expired;
    }
}
