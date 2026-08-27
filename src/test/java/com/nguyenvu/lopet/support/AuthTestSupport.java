package com.nguyenvu.lopet.support;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nguyenvu.lopet.security.jwt.UserPrincipal;

/**
 * Đặt danh tính người gọi cho các test chạy ở tầng SERVICE, nơi không có request HTTP nào để
 * {@code JwtAuthenticationFilter} nạp {@code SecurityContext}.
 *
 * <p>Dựng đúng hình dạng mà filter thật tạo ra — {@link UserPrincipal} làm principal của một
 * {@code UsernamePasswordAuthenticationToken} — chứ không mock {@code CurrentUser}: nhờ vậy test vẫn
 * đi qua đúng đường mà mã sản phẩm đọc danh tính, và một thay đổi ở filter sẽ làm test đỏ thay vì
 * trôi qua trong im lặng.
 *
 * <p><b>Bắt buộc gọi {@link #clear()} sau mỗi test.</b> {@code SecurityContextHolder} mặc định dùng
 * ThreadLocal, mà JUnit tái dùng thread giữa các test — bỏ bước dọn thì danh tính của test trước rò
 * sang test sau và mọi khẳng định về quyền mất nghĩa.
 */
public final class AuthTestSupport {

    /** Nhân danh tài khoản này cho lời gọi service kế tiếp */
    public static void actAs(Integer accountId) {
        actAs(accountId, List.of());
    }

    /** Như trên, kèm vai trò — dùng cho các luồng cần bypass của staff */
    public static void actAs(Integer accountId, List<String> roles) {
        UserPrincipal principal = new UserPrincipal(accountId, "test-" + accountId + "@test.local", roles);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList()));
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }

    private AuthTestSupport() {
    }
}
