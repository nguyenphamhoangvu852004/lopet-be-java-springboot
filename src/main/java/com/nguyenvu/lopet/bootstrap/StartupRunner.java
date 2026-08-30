package com.nguyenvu.lopet.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

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
