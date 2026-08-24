package com.eazybytes.accounts;

import com.eazybytes.accounts.entity.Accounts;
import com.eazybytes.accounts.repository.AccountsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cloud.stream.binder.test.EnableTestBinder;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("integration")
@EnableTestBinder
class OptimisticLockingIT {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private AccountsRepository accountsRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void secondUpdateFailsWhenFirstAlreadyBumpedTheVersion() {
        // TODO 1: seed one Accounts row with branchAddress="Madrid" and save it.
        //         Capture its accountNumber for later.
        Accounts accounts = new Accounts();
        accounts.setCustomerId(1L);
        accounts.setAccountType("Savings");
        accounts.setBranchAddress("Madrid");
        accounts.setCommunicationSw(false);
        accounts = accountsRepository.save(accounts);
        Long id = accounts.getAccountNumber();

        // TODO 2: first "user" transaction — load by id, change branchAddress
        //         to "Barcelona", commit (return from template).

        transactionTemplate.execute(status -> {
            Accounts loaded = accountsRepository.findById(id).orElseThrow();
            loaded.setBranchAddress("Barcelona");
            return null;
        });

        // TODO 3: assert the first update succeeded: reload from DB and check
        //         branchAddress == "Barcelona" and version == 1.
        Accounts afterFirstUpdate = accountsRepository.findById(id).orElseThrow();
        assertThat(afterFirstUpdate.getBranchAddress()).isEqualTo("Barcelona");
        assertThat(afterFirstUpdate.getVersion()).isEqualTo(1L);

        // TODO 4: simulate the stale second user — build an Accounts instance
        //         manually with the SAME accountNumber but version=0 (the value
        //         they read BEFORE the first user's update). Wrap the save
        //         in transactionTemplate.execute(...) and expect
        //         ObjectOptimisticLockingFailureException.
        //         Hint: to "build manually" use a fresh Accounts object with
        //         the required fields set (customerId, accountType, branchAddress,
        //         communicationSw, version=0) and the same accountNumber.
        //         accountsRepository.save() on a detached entity with a version
        //         mismatch triggers the check.
        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            Accounts stale = new Accounts();
            stale.setAccountNumber(id);          // same PK as the real row
            stale.setCustomerId(1L);      // same as the seed (any long is fine)
            stale.setAccountType("Savings");     // "Savings"
            stale.setBranchAddress("Sevilla");    // what user 2 wanted
            stale.setCommunicationSw(false);
            stale.setVersion(0L);                 // the outdated version — the trap

            return accountsRepository.save(stale);
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);


// Final check: user 2 was blocked, DB still holds user 1's change.
        Accounts finalState = accountsRepository.findById(id).orElseThrow();
        assertThat(finalState.getBranchAddress()).isEqualTo("Barcelona");
        assertThat(finalState.getVersion()).isEqualTo(1L);
    }
}
