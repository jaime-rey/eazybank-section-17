package com.eazybytes.accounts;

import com.eazybytes.accounts.entity.Accounts;
import com.eazybytes.accounts.entity.Customer;
import com.eazybytes.accounts.repository.AccountsRepository;
import com.eazybytes.accounts.repository.CustomerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.EnableTestBinder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
@EnableTestBinder
class CustomerAccountsIT extends AbstractMySqlIT {

    private static final Logger log = LoggerFactory.getLogger(CustomerAccountsIT.class);

    @Autowired private CustomerRepository customerRepository;
    @Autowired private AccountsRepository accountsRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Statistics stats() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    void loadsAccountsThroughBidirectionalNavigation() {
        Customer owner = newCustomer("Ada Lovelace");
        owner.addAccount(newAccount(owner, "Madrid"));
        owner.addAccount(newAccount(owner, "Barcelona"));
        owner.addAccount(newAccount(owner, "Sevilla"));

        customerRepository.save(owner);
        Long ownerId = owner.getCustomerId();

        entityManager.flush();
        entityManager.clear();

        Customer reloaded = customerRepository.findById(ownerId).orElseThrow();
        List<Accounts> accounts = reloaded.getAccounts();

        assertThat(accounts).hasSize(3);
        assertThat(accounts)
            .extracting(Accounts::getBranchAddress)
            .containsExactlyInAnyOrder("Madrid", "Barcelona", "Sevilla");
        assertThat(accounts).allSatisfy(a ->
            assertThat(a.getCustomer().getCustomerId()).isEqualTo(ownerId)
        );
    }

    @Test
    @Transactional
    void reproducesNPlusOneWhenIteratingCustomers() {
        // seed: 3 customers, cada uno con 2 accounts (6 accounts en total)
        for (int i = 1; i <= 3; i++) {
            Customer c = newCustomer("Customer " + i);
            c.addAccount(newAccount(c, "Branch " + i + "-A"));
            c.addAccount(newAccount(c, "Branch " + i + "-B"));
            customerRepository.save(c);
        }

        entityManager.flush();
        entityManager.clear();

        stats().clear();
        long queriesBefore = stats().getPrepareStatementCount();
        log.info(">>> BEGIN N+1 section - count the SELECTs <<<");
        List<Customer> customers = customerRepository.findAll();
        int totalAccounts = 0;
        for (Customer c : customers) {
            totalAccounts += c.getAccounts().size();
        }
        log.info(">>> END N+1 section <<<");
        long queriesAfter = stats().getPrepareStatementCount();
        long queriesExecuted = queriesAfter - queriesBefore;

        assertThat(customers).hasSize(3);
        assertThat(totalAccounts).isEqualTo(6);
        assertThat(queriesExecuted)
            .as("N+1: expected 1 select for customers + 3 for each accounts collection")
            .isEqualTo(4);
    }

    @Test
    @Transactional
    void fixesNPlusOneWithEntityGraph() {
        for (int i = 1; i <= 3; i++) {
            Customer c = newCustomer("Customer " + i);
            c.addAccount(newAccount(c, "Branch " + i + "-A"));
            c.addAccount(newAccount(c, "Branch " + i + "-B"));
            customerRepository.save(c);
        }
        entityManager.flush();
        entityManager.clear();

        stats().clear();
        long queriesBefore = stats().getPrepareStatementCount();
        log.info(">>> BEGIN @EntityGraph section - expect a single SELECT with JOIN <<<");
        List<Customer> customers = customerRepository.findAllWithAccounts();
        int totalAccounts = 0;
        for (Customer c : customers) {
            totalAccounts += c.getAccounts().size();
        }
        log.info(">>> END @EntityGraph section <<<");
        long queriesAfter = stats().getPrepareStatementCount();
        long queriesExecuted = queriesAfter - queriesBefore;

        assertThat(customers).hasSize(3);
        assertThat(totalAccounts).isEqualTo(6);
        assertThat(queriesExecuted)
            .as("@EntityGraph: expected 1 select with JOIN")
            .isEqualTo(1);
    }

    // ---------- fixtures ----------

    private static Customer newCustomer(String name) {
        Customer c = new Customer();
        c.setName(name);
        c.setEmail("owner-" + ThreadLocalRandom.current().nextLong(1, 1_000_000_000) + "@example.com");
        c.setMobileNumber(String.format("9%09d", ThreadLocalRandom.current().nextLong(0, 1_000_000_000)));
        return c;
    }

    private static Accounts newAccount(Customer owner, String branch) {
        Accounts a = new Accounts();
        a.setCustomer(owner);
        a.setAccountType("Savings");
        a.setBranchAddress(branch);
        a.setCommunicationSw(false);
        return a;
    }

}
