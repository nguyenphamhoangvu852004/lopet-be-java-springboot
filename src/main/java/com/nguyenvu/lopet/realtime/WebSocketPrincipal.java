package com.nguyenvu.lopet.realtime;

import java.security.Principal;

import com.nguyenvu.lopet.security.jwt.UserPrincipal;

/**
 * Danh tính gắn vào một phiên STOMP, do {@link StompAuthChannelInterceptor} đặt ở frame CONNECT.
 *
 * <p>Bọc {@link UserPrincipal} sẵn có thay vì dựng một kiểu danh tính thứ hai: token của WebSocket
 * và token của REST là cùng một token, đi qua cùng một {@code JwtService.parseAccessToken}, nên hai
 * đường không được phép hiểu claim khác nhau.
 *
 * <p>{@link #getName()} trả {@code accountId} chứ không phải email — đó là khoá mà destination
 * {@code /topic/user.<id>/...} dùng, và cũng là khoá mà cơ chế {@code /user/**} của Spring sẽ dùng
 * nếu sau này cần tới.
 *
 * <p>Handler nghiệp vụ lấy id qua {@link #accountId()}, KHÔNG BAO GIỜ từ payload client gửi lên —
 * xem {@code MessageStompController}.
 */
public record WebSocketPrincipal(UserPrincipal user) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(user.id());
    }

    public Integer accountId() {
        return user.id();
    }

    /** {@code null} khi principal không phải phiên WebSocket đã xác thực */
    public static Integer accountIdOf(Principal principal) {
        return principal instanceof WebSocketPrincipal ws ? ws.accountId() : null;
    }
}
