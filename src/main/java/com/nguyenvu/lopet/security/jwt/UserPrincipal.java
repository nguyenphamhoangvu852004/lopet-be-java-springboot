package com.nguyenvu.lopet.security.jwt;

import java.util.List;

/**
 * Bản sao của {@code UserPayload} trong {@code utils/jwt.util.ts}: đúng ba trường được ký vào token.
 *
 * <p>Danh tính dùng cho mọi kiểm tra phân quyền LUÔN lấy từ đây (tức từ token), không bao giờ từ
 * body hay path param — nếu không, kẻ tấn công chỉ cần gửi id của nạn nhân là qua được hết.
 */
public record UserPrincipal(Integer id, String email, List<String> roles) {

    public UserPrincipal {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
