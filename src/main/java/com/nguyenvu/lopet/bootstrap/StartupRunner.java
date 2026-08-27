package com.nguyenvu.lopet.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Trình tự khởi tạo dữ liệu lúc app lên.
 *
 * <p>Hai nhóm tách bạch nhau và KHÔNG được lẫn:
 * <ul>
 *   <li><b>Bắt buộc ở mọi môi trường</b> — {@link AuthorizationSeeder} (role/permission, app không
 *       phân quyền được nếu thiếu) và {@link AdminInitializer} (tài khoản quản trị). Cả hai đều
 *       idempotent.</li>
 *   <li><b>Dữ liệu demo</b> — {@link SeedDemoAccount}, {@link SeedDemoPosts}. Chỉ chạy khi
 *       {@code lopet.bootstrap.seed-demo=true}; cờ đó mặc định {@code false} trong application.yml
 *       và chỉ được bật lại ở profile {@code dev}. Trước đây chúng chạy vô điều kiện, nghĩa là mọi
 *       lần deploy production đều bơm thêm một tài khoản giả và 16 bài đăng faker vào DB thật.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupRunner implements ApplicationRunner {

    private final AuthorizationSeeder authorizationSeeder;
    private final AdminInitializer adminInitializer;
    private final SeedDemoAccount seedDemoAccount;
    private final SeedDemoPosts seedDemoPosts;

    @Value("${lopet.bootstrap.seed-demo:false}")
    private boolean seedDemo;

    @Override
    public void run(ApplicationArguments args) {
        authorizationSeeder.execute();
        adminInitializer.execute();

        if (!seedDemo) {
            log.info("Bỏ qua dữ liệu demo (lopet.bootstrap.seed-demo=false)");
            return;
        }
        log.info("Seed dữ liệu demo (lopet.bootstrap.seed-demo=true)");
        seedDemoAccount.execute();
        seedDemoPosts.execute();
    }
}
