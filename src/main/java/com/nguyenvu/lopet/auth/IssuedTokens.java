package com.nguyenvu.lopet.auth;

/**
 * Kết quả nội bộ của việc cấp phiên: service trả về ĐỦ cả hai token, controller mới quyết định
 * token nào ra body và token nào ra cookie. Tách khỏi các DTO trong {@code auth.dto} để refresh
 * token không bao giờ lọt vào một record nằm trên đường serialize ra JSON — nhầm lẫn kiểu đó chính
 * là thứ thay đổi này đang gỡ bỏ.
 */
record IssuedTokens(Integer id, String accessToken, String refreshToken) {
}
