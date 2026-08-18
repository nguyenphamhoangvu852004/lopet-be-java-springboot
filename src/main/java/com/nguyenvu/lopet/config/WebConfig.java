package com.nguyenvu.lopet.config;

import com.nguyenvu.lopet.security.AuthInterceptor;
import com.nguyenvu.lopet.security.petcontext.PetContextInterceptor;
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
    private final PetContextInterceptor petContextInterceptor;

    @Value("${lopet.cors.allowed-origins}")
    private String[] allowedOrigins;

    /**
     * Thứ tự BẮT BUỘC: {@code authInterceptor} trước, {@code petContextInterceptor} sau. Interceptor
     * thứ hai cần accountId đã xác thực để so quyền sở hữu của {@code X-Pet-Id}; đảo thứ tự thì mọi
     * endpoint tương tác đều trả 403 vì chưa có danh tính nào để so.
     *
     * <p>{@code order()} khai tường minh thay vì dựa vào thứ tự gọi {@code addInterceptor}: thứ tự
     * ngầm định đúng cho tới lúc ai đó chèn một interceptor thứ ba vào giữa.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).order(0);
        registry.addInterceptor(petContextInterceptor).order(1);
    }

    /**
     * Trùng {@code corsMiddleware}: origin {@code *}, đúng 5 method, và danh sách header cho phép
     * y hệt. Không bật {@code allowCredentials} vì bản TS cũng không bật (và không thể bật cùng
     * origin {@code *}).
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
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
                .allowedHeaders("*");
    }
}
