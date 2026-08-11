package com.nguyenvu.lopet.common.exception;

/**
 * Lỗi token trên các route dùng {@code verifyToken()} của lopet-be.
 *
 * <p>Cố ý KHÔNG phải 401. Ở bên TS, {@code verifyToken()} bắt mọi lỗi của thư viện jsonwebtoken rồi
 * gọi {@code next(error)} nguyên trạng; {@code globalExceptionMiddleware} đọc {@code error.code}
 * mà các lớp lỗi của jsonwebtoken lại không có trường đó, nên response thực tế là <b>500</b> kèm
 * message thô ("jwt expired", "invalid signature", "jwt malformed").
 *
 * <p>Chỉ {@code optionalAuth()} mới chuẩn hoá về 401 — xem {@link UnauthorizedException} và
 * AuthInterceptor. Sự khác biệt giữa hai route là hành vi thật của backend cũ, không phải nhầm lẫn.
 */
public class RawJwtException extends HttpException {

    public RawJwtException(String message) {
        super(500, message);
    }
}
