package com.eazybytes.accounts;

import com.eazybytes.accounts.entity.Customer;
import com.eazybytes.accounts.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cloud.stream.binder.test.EnableTestBinder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// Testcontainers + MySQL real. Requiere Docker en ejecución.
// La conexión al motor Linux de Docker Desktop y la API version se fijan en el
// pom (maven-failsafe-plugin: DOCKER_HOST env var + -Dapi.version). Ver pom.xml.
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("integration")
@EnableTestBinder
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

        assertThat(saved.getCustomerId()).isNotNull().isPositive();
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Alice Testcontainers");
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getCreatedBy()).isEqualTo("ACCOUNTS_MS");
    }
}