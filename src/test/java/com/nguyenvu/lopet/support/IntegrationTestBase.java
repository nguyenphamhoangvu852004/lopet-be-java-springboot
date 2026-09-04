package com.nguyenvu.lopet.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Autowired
    protected TransactionTemplate transactionTemplate;

    protected <T> T inTransaction(java.util.function.Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }

    protected void inTransaction(Runnable action) {
        transactionTemplate.executeWithoutResult(status -> action.run());
    }

    public static String redisUrl(int database) {
        String host = System.getenv().getOrDefault("TEST_REDIS_HOST", "localhost");
        String port = System.getenv().getOrDefault("TEST_REDIS_PORT", "6379");
        return "redis://" + host + ":" + port + "/" + database;
    }

    public static String jdbcUrl(String database) {
        String host = System.getenv().getOrDefault("TEST_DB_HOST", "127.0.0.1");
        String port = System.getenv().getOrDefault("TEST_DB_PORT", "3307");
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true"
                + "&useSSL=false&createDatabaseIfNotExist=true";
    }
}
