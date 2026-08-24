package com.eazybytes.accounts;

import com.eazybytes.accounts.dto.BeneficiarySummary;
import com.eazybytes.accounts.entity.AccountBeneficiary;
import com.eazybytes.accounts.entity.Accounts;
import com.eazybytes.accounts.entity.Beneficiary;
import com.eazybytes.accounts.repository.AccountBeneficiaryRepository;
import com.eazybytes.accounts.repository.AccountsRepository;
import com.eazybytes.accounts.repository.BeneficiaryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cloud.stream.binder.test.EnableTestBinder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("integration")
@EnableTestBinder
class AccountBeneficiaryIT {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired private AccountsRepository accountsRepository;
    @Autowired private BeneficiaryRepository beneficiaryRepository;
    @Autowired private AccountBeneficiaryRepository accountBeneficiaryRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    void reproducesNPlusOneWhenAccessingBeneficiariesNaively() {
        seedData();

        System.out.println(">>> BEGIN naive N+1 section — count the SELECTs <<<");
        List<Accounts> accounts = accountsRepository.findAll();
        for (Accounts account : accounts) {
            List<AccountBeneficiary> links =
                accountBeneficiaryRepository.findByIdAccountNumber(account.getAccountNumber());
            for (AccountBeneficiary ab : links) {
                // Accessing the name forces lazy loading of Beneficiary → extra SELECT.
                String name = ab.getBeneficiary().getFullName();
                assertThat(name).isNotEmpty();
            }
        }
        System.out.println(">>> END naive N+1 section <<<");
    }

    @Test
    @Transactional
    void fixesNPlusOneWithEntityGraph() {
        List<Long> accountNumbers = seedData();

        System.out.println(">>> BEGIN fix A (@EntityGraph) <<<");
        List<AccountBeneficiary> links =
            accountBeneficiaryRepository.findByIdAccountNumberIn(accountNumbers);
        for (AccountBeneficiary ab : links) {
            // beneficiary is already hydrated (implicit JOIN FETCH) → no extra SELECTs.
            String name = ab.getBeneficiary().getFullName();
            assertThat(name).isNotEmpty();
        }
        System.out.println(">>> END fix A (@EntityGraph) <<<");

        assertThat(links).hasSize(9); // 3 accounts x 3 links per account
    }

    @Test
    @Transactional
    void fixesNPlusOneWithNativeSqlProjection() {
        List<Long> accountNumbers = seedData();

        System.out.println(">>> BEGIN fix B (native SQL + projection) <<<");
        List<BeneficiarySummary> summaries =
            accountBeneficiaryRepository.summariesForAccountsNative(accountNumbers);
        for (BeneficiarySummary s : summaries) {
            // Everything comes from the projection — no entity, no proxy.
            assertThat(s.getFullName()).isNotEmpty();
            assertThat(s.getPercentage()).isNotNull();
            assertThat(s.getAccountNumber()).isNotNull();
        }
        System.out.println(">>> END fix B (native SQL + projection) <<<");

        assertThat(summaries).hasSize(9);
    }

    // Seed 3 accounts + 4 beneficiaries + 9 links. Returns the DB-generated
    // account numbers so tests can query by them. Each test runs in its own
    // @Transactional that rolls back on completion.
    private List<Long> seedData() {
        List<Accounts> accounts = List.of(newAccount(), newAccount(), newAccount());
        accountsRepository.saveAll(accounts);

        List<Beneficiary> people = List.of(
            newBeneficiary("DOC-001", "Alice Alpha"),
            newBeneficiary("DOC-002", "Bob Beta"),
            newBeneficiary("DOC-003", "Carol Gamma"),
            newBeneficiary("DOC-004", "Dave Delta")
        );
        beneficiaryRepository.saveAll(people);

        for (Accounts account : accounts) {
            for (int i = 0; i < 3; i++) {
                AccountBeneficiary ab = new AccountBeneficiary();
                ab.setAccount(account);
                ab.setBeneficiary(people.get(i));
                ab.setPercentage(new BigDecimal("33.33"));
                accountBeneficiaryRepository.save(ab);
            }
        }

        // Flush to MySQL and clear the identity map: subsequent SELECTs are REAL
        // reads against the DB, not persistence-context cache hits.
        entityManager.flush();
        entityManager.clear();

        return accounts.stream().map(Accounts::getAccountNumber).toList();
    }

    private static Accounts newAccount() {
        Accounts a = new Accounts();
        a.setCustomerId(ThreadLocalRandom.current().nextLong(1, 10_000));
        a.setAccountType("Savings");
        a.setBranchAddress("123 Test St");
        a.setCommunicationSw(false);
        return a;
    }

    private static Beneficiary newBeneficiary(String doc, String fullName) {
        Beneficiary b = new Beneficiary();
        b.setDocumentNumber(doc);
        b.setFullName(fullName);
        return b;
    }
}
