package com.eazybytes.accounts;

import com.eazybytes.accounts.entity.Customer;
import com.eazybytes.accounts.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.EnableTestBinder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// Testcontainers + MySQL real. Requiere Docker en ejecución.
// La conexión al motor Linux de Docker Desktop y la API version se fijan en el
// pom (maven-failsafe-plugin: DOCKER_HOST env var + -Dapi.version). Ver pom.xml.
@SpringBootTest
@ActiveProfiles("integration")
@EnableTestBinder
class AccountsRepositoryIT extends AbstractMySqlIT {

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void mysqlContainerIsRunning() {
        assertThat(MYSQL.isRunning()).isTrue();
        System.out.println(">>> MySQL container JDBC URL: " + MYSQL.getJdbcUrl());
    }

    @Test
    @Transactional
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