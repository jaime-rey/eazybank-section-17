package com.eazybytes.accounts;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Singleton MySQL container shared across every IT.
 *
 * Why not @Testcontainers + @Container: that extension calls stop() at the end
 * of each test class, so every IT would boot its own container. Starting the
 * container in a static block and never stopping it lets Testcontainers reuse
 * the same one — within a run, and (with reuse enabled in
 * ~/.testcontainers.properties) across runs too.
 *
 * Because the container is reused, schema.sql only runs on first boot and any
 * committed data survives across tests. cleanDatabase() truncates every table
 * before each test so ITs start from a clean slate, even non-transactional ones
 * (e.g. OptimisticLockingIT, which needs real commits).
 */
abstract class AbstractMySqlIT {

    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4").withReuse(true);

    static {
        MYSQL.start();
    }

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void cleanDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            statement.execute("TRUNCATE TABLE account_beneficiary");
            statement.execute("TRUNCATE TABLE accounts");
            statement.execute("TRUNCATE TABLE beneficiary");
            statement.execute("TRUNCATE TABLE customer");
            statement.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }
}
