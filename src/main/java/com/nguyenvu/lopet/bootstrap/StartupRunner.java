package com.nguyenvu.lopet.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tái hiện đúng thứ tự của {@code bootstrap()} trong {@code src/index.ts}:
 * DB → Redis → seed phân quyền → tài khoản admin → mở cổng.
 *
 * <p>Bước "verify SMTP" của bản TS đã bỏ: mail giờ đi qua HTTP API của Resend, không có kết nối
 * thường trực để kiểm tra lúc khởi động. Cấu hình thiếu được cảnh báo trong
 * {@link com.nguyenvu.lopet.config.ResendConfig}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupRunner implements ApplicationRunner {

    private final AuthorizationSeeder authorizationSeeder;
    private final AdminInitializer adminInitializer;

    @Override
    public void run(ApplicationArguments args) {
         authorizationSeeder.seed();
         adminInitializer.init();
    }
}
