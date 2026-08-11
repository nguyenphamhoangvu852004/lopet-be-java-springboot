package com.nguyenvu.lopet.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tương đương việc gắn {@code verifyToken()} hoặc {@code optionalAuth()} lên một route Express.
 *
 * <p>Không gắn annotation = route công khai, token (nếu client vẫn gửi) hoàn toàn bị bỏ qua — đúng
 * như các route đọc profile/group bên TS.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Auth {

    /**
     * {@code true} → verifyToken(): thiếu token trả 400 "Token not found", token hỏng trả 500 kèm
     * message thô của thư viện JWT.
     *
     * <p>{@code false} → optionalAuth(): thiếu token thì đi tiếp như khách, token hỏng trả 401
     * "Token không hợp lệ hoặc đã hết hạn". Cố ý không âm thầm hạ xuống thành khách: token hết hạn
     * mà lặng lẽ mất bài trong feed là lỗi rất khó lần ra.
     */
    boolean required() default true;
}
