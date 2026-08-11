package com.nguyenvu.lopet.auth.dto;

/**
 * Cố ý KHÔNG chứa roles: bản TS chỉ trả ba trường này, danh sách role nằm trong payload JWT và
 * client tự giải mã. Thêm roles vào đây là đổi contract.
 */
public record LoginResponse(Integer id, String accessToken, String refreshToken) {
}
