package com.nguyenvu.lopet.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.security.jwt.UserPrincipal;

/**
 * Truy cập danh tính người gọi. Tương đương {@code req.user} của Express — và cũng mang đúng ràng
 * buộc đó: đây là nguồn danh tính DUY NHẤT được phép dùng cho kiểm tra phân quyền, không bao giờ
 * lấy từ body hay path param.
 */
public final class CurrentUser {

    /** {@code null} khi người gọi là khách (route dùng {@code @Auth(required = false)}) */
    public static UserPrincipal optional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return null;
        }
        return principal;
    }

    /** Dùng trong các luồng đã có {@code @Auth(required = true)} nên chắc chắn có danh tính */
    public static UserPrincipal require() {
        UserPrincipal principal = optional();
        if (principal == null) {
            throw new ForbiddenException("Chưa xác thực");
        }
        return principal;
    }

    /** Id người xem cho các luồng đọc có lọc quyền riêng tư; {@code null} = khách chưa đăng nhập */
    public static Integer viewerId() {
        UserPrincipal principal = optional();
        return principal == null ? null : principal.id();
    }

    private CurrentUser() {
    }
}
