package com.nguyenvu.lopet.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tầng 1 của phân quyền: "hành động này có được phép không?" — tương đương
 * {@code requirePermission(...)} của Express.
 *
 * <p>Nhiều mã trong {@link #value()} được hiểu theo nghĩa <b>HOẶC</b>, đúng như
 * {@code required.some(...)} bên TS.
 *
 * <p>Cố ý không dùng {@code hasRole()}: đó là câu hỏi sai altitude, mỗi lần thêm role lại phải sửa
 * tay hàng loạt endpoint. Nếu tài nguyên có chủ sở hữu thì còn phải qua tầng ownership nữa —
 * permission không thay thế được ownership.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    String[] value();
}
