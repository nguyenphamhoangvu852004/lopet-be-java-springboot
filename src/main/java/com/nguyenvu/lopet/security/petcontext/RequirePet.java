package com.nguyenvu.lopet.security.petcontext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu endpoint TƯƠNG TÁC: hành động được thực hiện nhân danh một thú cưng, nên request phải
 * mang header {@code X-Pet-Id} và {@link PetContextInterceptor} phải xác nhận pet đó thuộc tài khoản
 * trong JWT.
 *
 * <p>Là tầng thứ BA của phân quyền, độc lập với hai tầng có sẵn:
 * <ul>
 *   <li>{@code @RequirePermission} — "hành động này có được phép không?"</li>
 *   <li>{@code OwnershipGuard} — "được phép làm lên ĐÚNG tài nguyên này không?"</li>
 *   <li>{@code @RequirePet} — "đang hành động NHÂN DANH ai?"</li>
 * </ul>
 * Ba câu hỏi khác nhau; gộp chúng vào một annotation sẽ khiến mỗi lần thêm một loại tương tác mới
 * lại phải sửa cả ba.
 *
 * <p><b>KHÔNG nhúng petId vào JWT.</b> Token chỉ mang accountId. Một người có nhiều pet và đổi qua
 * lại giữa chúng liên tục; nhét petId vào token nghĩa là mỗi lần đổi pet phải cấp lại token, và một
 * token cũ bị rò rỉ vẫn hành động được nhân danh pet đã chuyển chủ.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePet {
}
