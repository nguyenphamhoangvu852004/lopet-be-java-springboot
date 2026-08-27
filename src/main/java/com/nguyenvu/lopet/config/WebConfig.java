package com.nguyenvu.lopet.config;

import java.util.Arrays;

import com.nguyenvu.lopet.security.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Value("${lopet.cors.allowed-origins}")
    private String[] allowedOrigins;

    /**
     * {@code order()} khai tường minh thay vì dựa vào thứ tự gọi {@code addInterceptor}: thứ tự ngầm
     * định đúng cho tới lúc ai đó chèn interceptor thứ hai vào. {@code authInterceptor} đặt danh tính
     * cho cả request nên bất cứ interceptor nào thêm sau đều phải chạy sau nó.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).order(0);
    }

    /**
     * Giữ đúng {@code corsMiddleware} của bản TS: 5 method và danh sách header cho phép y hệt.
     *
     * <p>Điểm khác duy nhất là {@code allowCredentials}, và nó CÓ ĐIỀU KIỆN. Refresh token nay nằm
     * trong cookie nên trình duyệt chỉ gửi kèm khi request đặt {@code credentials: 'include'} VÀ
     * server cho phép credentials — thiếu vế sau thì {@code /v1/auth/refresh} luôn thấy cookie rỗng.
     *
     * <p>Nhưng bật kèm origin {@code *} thì mọi trang web đều gọi được {@code /v1/auth/refresh}
     * bằng cookie của nạn nhân VÀ đọc được access token trong body — tức là chiếm tài khoản. Nên
     * credentials chỉ bật khi {@code DOMAIN_CORS} liệt kê origin cụ thể; để {@code *} thì cookie
     * không dùng được cross-origin, và đó là lựa chọn an toàn hơn trong hai cái sai.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        boolean wildcard = Arrays.asList(allowedOrigins).contains("*");
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods(
                        "GET",
                        "POST",
                        "PUT",
                        "DELETE",
                        "PATCH",
                        "OPTIONS"
                )
                .allowedHeaders("*")
                .allowCredentials(!wildcard);
    }
}
