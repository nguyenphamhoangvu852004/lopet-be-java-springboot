package com.nguyenvu.lopet.security.petcontext;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import com.nguyenvu.lopet.common.exception.MissingPetHeaderException;

/**
 * Truy cập "pet đang thao tác" — đối xứng với {@link com.nguyenvu.lopet.security.CurrentUser}, và
 * mang đúng ràng buộc đó: đây là nguồn DUY NHẤT được phép dùng làm tác giả của một hành động, không
 * bao giờ lấy petId từ body hay path param.
 *
 * <p>Giá trị được {@link PetContextInterceptor} đặt vào SAU khi đã xác nhận pet thuộc tài khoản
 * trong JWT. Đọc thẳng header {@code X-Pet-Id} ở service là bỏ qua toàn bộ bước xác nhận đó.
 *
 * <p>Dùng request attribute chứ không phải {@code ThreadLocal} tự quản: Spring đã dọn attribute khi
 * request kết thúc, còn ThreadLocal trên pool thread của Tomcat mà quên xoá thì petId của người này
 * rò sang request của người khác.
 */
public final class PetContext {

    static final String ATTRIBUTE = "lopet.petContext.petId";

    /**
     * Pet đang thao tác, hoặc {@code null}.
     *
     * <p>Có giá trị khi client gửi {@code X-Pet-Id} và interceptor đã xác nhận con vật đó thuộc tài
     * khoản trong token — kể cả ở route không mang {@code @RequirePet}, nơi header là tuỳ chọn. Trả
     * {@code null} khi khách chưa đăng nhập, khi không có header, khi pet không thuộc người gọi, hoặc
     * khi request không đi qua interceptor nào (job nền, handler socket).
     *
     * <p>Người gọi phải chịu được {@code null} và coi đó là "chưa chọn pet" — không bao giờ suy ra
     * quyền từ việc thiếu giá trị.
     */
    public static Integer optional() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        Object petId = attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return petId instanceof Integer value ? value : null;
    }

    /**
     * Dùng trong các use case đã có {@code @RequirePet} nên chắc chắn có giá trị.
     *
     * <p>Ném 400 thay vì trả null: một service tương tác gọi tới đây mà không có pet nghĩa là
     * endpoint quên gắn {@code @RequirePet} — hỏng cấu hình, không phải trạng thái hợp lệ.
     */
    public static Integer require() {
        Integer petId = optional();
        if (petId == null) {
            throw new MissingPetHeaderException();
        }
        return petId;
    }

    private PetContext() {
    }
}
