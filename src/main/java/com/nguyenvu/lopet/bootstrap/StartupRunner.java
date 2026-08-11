package com.nguyenvu.lopet.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tái hiện đúng thứ tự của {@code bootstrap()} trong {@code src/index.ts}:
 * DB → Redis → verify SMTP → seed phân quyền → tài khoản admin → mở cổng.
 *
 * <p>Lỗi SMTP chỉ được LOG, không chặn khởi động — giống bản TS, vì máy dev thường không mở được
 * cổng 587 và điều đó không nên làm sập cả backend.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupRunner implements ApplicationRunner {

    private final JavaMailSenderImpl mailSender;
    private final AuthorizationSeeder authorizationSeeder;
    private final AdminInitializer adminInitializer;

    @Override
    public void run(ApplicationArguments args) {
        try {
            mailSender.testConnection();
            log.info("SMTP is ready");
        } catch (Exception exception) {
            log.error("SMTP verify failed: {}", exception.getMessage());
        }

         authorizationSeeder.seed();
         adminInitializer.init();
    }
}
