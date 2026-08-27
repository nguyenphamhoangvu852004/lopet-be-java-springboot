package com.nguyenvu.lopet.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Nơi duy nhất biết refresh token nằm ở cookie nào và mang thuộc tính gì. Gom lại một chỗ vì
 * ba điểm chạm (đăng nhập, gia hạn, xoá phiên) phải ghi CÙNG một bộ thuộc tính — trình duyệt coi
 * cookie khác {@code Path}/{@code Domain} là cookie khác, nên lệch một thuộc tính là sinh ra cookie
 * thứ hai thay vì đè lên cookie cũ, và phiên cũ sống tiếp tới lúc hết hạn.
 *
 * <p>Dùng {@link Cookie} của servlet thay vì {@code ResponseCookie} của Spring: {@code SameSite}
 * được đặt qua {@link Cookie#setAttribute} (Servlet 6.0) nên vẫn khai báo đủ thuộc tính cần thiết.
 *
 * <p>{@code HttpOnly} là lý do tồn tại của cả thay đổi này — JavaScript không đọc được refresh
 * token, nên một lỗ XSS chỉ lấy được access token còn sống 1 giờ thay vì chiếc chìa khoá gia hạn
 * phiên suốt 10 giờ.
 */
@Component
public class RefreshTokenCookie {

    private final String name;
    private final String path;
    private final boolean secure;
    private final String sameSite;
    private final int maxAgeSeconds;

    public RefreshTokenCookie(@Value("${lopet.auth.refresh-cookie.name}") String name,
                              @Value("${lopet.auth.refresh-cookie.path}") String path,
                              @Value("${lopet.auth.refresh-cookie.secure}") boolean secure,
                              @Value("${lopet.auth.refresh-cookie.same-site}") String sameSite,
                              @Value("${lopet.jwt.refresh-token-expires-in}") long refreshTtlSeconds) {
        this.name = name;
        this.path = path;
        this.secure = secure;
        this.sameSite = sameSite;
        // Cookie sống đúng bằng token nó chứa: hết hạn sớm hơn thì người dùng bị đá ra dù token còn
        // hiệu lực, muộn hơn thì client gửi lên một token chắc chắn bị từ chối.
        this.maxAgeSeconds = (int) refreshTtlSeconds;
    }

    public String getName() {
        return name;
    }

    /** Ghi refresh token mới. Dùng cho cả đăng nhập lẫn xoay vòng token khi gia hạn. */
    public void write(HttpServletResponse response, String refreshToken) {
        response.addCookie(build(refreshToken, maxAgeSeconds));
    }

    /**
     * Xoá cookie bằng cách ghi đè giá trị rỗng với {@code Max-Age=0} — không có API xoá cookie phía
     * server, chỉ có cách bảo trình duyệt tự bỏ.
     */
    public void clear(HttpServletResponse response) {
        response.addCookie(build("", 0));
    }

    /** {@code null} khi client không gửi cookie hoặc gửi cookie rỗng. */
    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                String value = cookie.getValue();
                return value == null || value.isBlank() ? null : value;
            }
        }
        return null;
    }

    private Cookie build(String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath(path);
        cookie.setMaxAge(maxAge);
        if (sameSite != null && !sameSite.isBlank()) {
            // SameSite=None bắt buộc đi kèm Secure, nếu không trình duyệt bỏ luôn cookie. Không tự
            // sửa ở đây: im lặng đổi thuộc tính bảo mật khiến cấu hình sai trông như đang chạy được.
            cookie.setAttribute("SameSite", sameSite);
        }
        return cookie;
    }
}
