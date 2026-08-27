package com.nguyenvu.lopet.auth.dto;

/**
 * Cùng bộ trường với {@link LoginResponse} để client dùng lại đúng một nhánh xử lý cho cả đăng nhập
 * lẫn gia hạn. Tách record riêng vì hai endpoint là hai hợp đồng độc lập — đổi một bên không được
 * kéo bên kia đổi theo.
 *
 * <p>Refresh token mới (xoay vòng) nằm ở header {@code Set-Cookie}, không nằm trong body.
 */
public record RefreshTokenResponse(Integer id, String accessToken) {
}
