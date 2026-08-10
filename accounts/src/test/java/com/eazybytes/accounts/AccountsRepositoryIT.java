package com.eazybytes.accounts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

// Solo se ejecuta cuando CI=true (GitHub Actions lo setea). Local se salta:
// Docker Desktop 4.86 + docker-java tienen incompatibilidad conocida en el /info endpoint.
@SpringBootTest
@Testcontainers
@ActiveProfiles("integration")
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class AccountsRepositoryIT {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Test
    void mysqlContainerIsRunning() {
        assertThat(mysql.isRunning()).isTrue();
        System.out.println(">>> MySQL container JDBC URL: " + mysql.getJdbcUrl());
    }
}
