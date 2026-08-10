package com.eazybytes.accounts;

import com.eazybytes.accounts.entity.Customer;
import com.eazybytes.accounts.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

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

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void mysqlContainerIsRunning() {
        assertThat(mysql.isRunning()).isTrue();
        System.out.println(">>> MySQL container JDBC URL: " + mysql.getJdbcUrl());
    }

    @Test
    void saveAndFindCustomerByMobileNumber() {
        Customer alice = new Customer();
        alice.setName("Alice Testcontainers");
        alice.setEmail("alice@testcontainers.io");
        alice.setMobileNumber("5559999999");

        Customer saved = customerRepository.save(alice);
        Optional<Customer> found = customerRepository.findByMobileNumber("5559999999");

        // AUTO_INCREMENT real de MySQL asigna el customerId
        assertThat(saved.getCustomerId()).isNotNull().isPositive();
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Alice Testcontainers");
        // JPA Auditing rellena created_at (LocalDateTime.now) y created_by (AuditAwareImpl -> "ACCOUNTS_MS")
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getCreatedBy()).isEqualTo("ACCOUNTS_MS");
    }
}
