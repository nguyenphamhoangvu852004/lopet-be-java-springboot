package com.nguyenvu.lopet.common.exception;

/**
 * Envelope lỗi nghiệp vụ, khớp {@code globalExceptionMiddleware}: chỉ hai khoá, KHÔNG có
 * {@code data}, {@code timestamp} hay {@code path}.
 */
public record ErrorResponse(int statusCode, String message) {
}
