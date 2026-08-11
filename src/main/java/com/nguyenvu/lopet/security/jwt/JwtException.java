package com.nguyenvu.lopet.security.jwt;

import lombok.Getter;

/**
 * Lỗi khi giải mã token. {@code reason} được đặt trùng nguyên văn với message của thư viện
 * jsonwebtoken bên TS, vì trên các route dùng {@code verifyToken()} chuỗi này lọt thẳng ra body
 * response (500 + message thô) — xem
 * {@link com.nguyenvu.lopet.common.exception.RawJwtException}.
 */
@Getter
public class JwtException extends RuntimeException {

    /** {@code TokenExpiredError} của jsonwebtoken */
    public static final String EXPIRED = "jwt expired";
    /** chữ ký sai */
    public static final String INVALID_SIGNATURE = "invalid signature";
    /** không đúng dạng ba đoạn base64url */
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
