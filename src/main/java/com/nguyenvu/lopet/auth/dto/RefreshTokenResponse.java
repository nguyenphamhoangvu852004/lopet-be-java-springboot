package com.nguyenvu.lopet.auth.dto;

/**
 * Cùng ba trường với {@link LoginResponse} để client dùng lại đúng một nhánh xử lý cho cả đăng nhập
 * lẫn gia hạn. Tách record riêng vì hai endpoint là hai hợp đồng độc lập — đổi một bên không được
 * kéo bên kia đổi theo.
 *
 * <p>{@code refreshToken} LUÔN là token mới (xoay vòng), không phải token client vừa gửi lên.
 */
public record RefreshTokenResponse(Integer id, String accessToken, String refreshToken) {
}
