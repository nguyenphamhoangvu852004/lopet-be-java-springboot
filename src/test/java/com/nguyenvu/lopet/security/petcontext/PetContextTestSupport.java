package com.nguyenvu.lopet.security.petcontext;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Đặt "pet đang thao tác" cho test tầng service, đúng cách {@link PetContextInterceptor} làm ở
 * runtime — đối xứng với việc test nạp {@code UserPrincipal} vào {@code SecurityContextHolder}.
 *
 * <p>Nằm trong package {@code security.petcontext} chứ không phải {@code support}: khoá request
 * attribute là chi tiết nội bộ của {@link PetContext} và phải giữ nguyên tầm nhìn package-private.
 * Chép giá trị chuỗi đó sang một lớp helper ở package khác nghĩa là hằng số này có hai bản, và bản
 * trong test sẽ âm thầm lệch đi vào ngày ai đó đổi bản kia.
 *
 * <p>Test gọi thẳng service KHÔNG đi qua interceptor nào, nên bước xác nhận "pet thuộc tài khoản
 * trong token" không chạy. Điều đó là đúng cho mục đích ở đây — thứ đang được kiểm là hành vi của
 * service khi đã có pet hợp lệ; còn bản thân bước xác nhận là việc của test dành cho interceptor.
 */
public final class PetContextTestSupport {

    /** Gắn petId vào request hiện tại; gọi lại với giá trị khác để đổi pet giữa hai lời gọi service */
    public static void actAs(Integer petId) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            attributes = new ServletRequestAttributes(new MockHttpServletRequest());
            RequestContextHolder.setRequestAttributes(attributes);
        }
        attributes.setAttribute(PetContext.ATTRIBUTE, petId, RequestAttributes.SCOPE_REQUEST);
    }

    /** Bắt buộc gọi ở {@code @AfterEach}: RequestContextHolder dùng ThreadLocal, và JUnit tái dùng thread */
    public static void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    private PetContextTestSupport() {
    }
}
