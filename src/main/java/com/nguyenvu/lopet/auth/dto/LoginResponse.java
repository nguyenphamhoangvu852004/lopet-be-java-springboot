package com.nguyenvu.lopet.auth.dto;

/**
 * Cố ý KHÔNG chứa roles: bản TS chỉ trả các trường này, danh sách role nằm trong payload JWT và
 * client tự giải mã. Thêm roles vào đây là đổi contract.
 *
 * <p>Cũng KHÔNG còn chứa {@code refreshToken}: token đó đi ra bằng cookie {@code HttpOnly} (xem
 * {@code RefreshTokenCookie}). Thêm lại trường này là mở đường cho JavaScript đọc được refresh
 * token, tức là bỏ đúng thứ mà cookie đang bảo vệ.
 */
public record LoginResponse(Integer id, String accessToken) {
}
