package com.nguyenvu.lopet.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Nền chung cho các test tích hợp: chạy trên MySQL THẬT của docker-compose (cổng 3307), database
 * riêng {@code lopet_java_test}, KHÔNG đụng tới database dev.
 *
 * <p>Vì sao không mock repository: phần dễ sai nhất của tầng quyền riêng tư là câu SQL — subquery
 * EXISTS, alias, và cách Hibernate dựng câu lệnh — mà mock thì không kiểm được dòng nào trong đó.
 *
 * <p>YÊU CẦU: {@code docker compose up -d mysql-docker redis} trong thư mục lopet-be.
 */
// Web environment MOCK (mặc định) chứ không phải NONE: Spring Security chỉ đăng ký bean
// HttpSecurity trong ngữ cảnh web, nên SecurityConfig không dựng được nếu tắt hẳn tầng web.
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Autowired
    protected TransactionTemplate transactionTemplate;

    /** Chạy một đoạn đọc dữ liệu trong transaction — cần cho mọi truy cập quan hệ lazy */
    protected <T> T inTransaction(java.util.function.Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }

    protected void inTransaction(Runnable action) {
        transactionTemplate.executeWithoutResult(status -> action.run());
    }

    /** URL tới MySQL của docker-compose, tự tạo database nếu chưa có */
    public static String jdbcUrl(String database) {
        String host = System.getenv().getOrDefault("TEST_DB_HOST", "127.0.0.1");
        String port = System.getenv().getOrDefault("TEST_DB_PORT", "3307");
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true"
                + "&useSSL=false&createDatabaseIfNotExist=true";
    }
}
