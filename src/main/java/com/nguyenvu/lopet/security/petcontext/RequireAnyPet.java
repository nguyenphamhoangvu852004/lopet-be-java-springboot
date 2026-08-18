package com.nguyenvu.lopet.security.petcontext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu endpoint tương tác mà TÁC NHÂN vẫn là tài khoản, không phải một con pet cụ thể — nhắn
 * tin, kết bạn. Điều kiện duy nhất: tài khoản phải sở hữu ít nhất một thú cưng.
 *
 * <p>Vì sao tách khỏi {@link RequirePet} thay vì bắt cả hai gửi {@code X-Pet-Id}: yêu cầu một header
 * mà không dùng tới giá trị của nó là hợp đồng API nói dối. Client sẽ gửi bừa một petId bất kỳ, và
 * ngày nào đó sẽ có người tin rằng giá trị ấy mang ý nghĩa.
 *
 * <p>Quy tắc nghiệp vụ đứng sau cả hai annotation thì giống nhau và đến từ mô hình mới: mạng xã hội
 * này lấy pet làm thực thể hoạt động, nên một tài khoản chưa có pet chưa thật sự tham gia vào nó —
 * xem {@link com.nguyenvu.lopet.common.exception.NoPetOwnedException}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAnyPet {
}
