package com.eazybytes.accounts;

import com.eazybytes.accounts.entity.Accounts;
import com.eazybytes.accounts.repository.AccountsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.EnableTestBinder;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eazybytes.accounts.entity.Customer;
import com.eazybytes.accounts.repository.CustomerRepository;

@SpringBootTest
@ActiveProfiles("integration")
@EnableTestBinder
class OptimisticLockingIT extends AbstractMySqlIT {

    @Autowired
    private AccountsRepository accountsRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void secondUpdateFailsWhenFirstAlreadyBumpedTheVersion() {
        Customer customer = new Customer();
        customer.setName("Owner");
        customer.setEmail("owner@example.com");
        customer.setMobileNumber("9345432123");
        customer = customerRepository.save(customer);

        Accounts accounts = new Accounts();
        accounts.setCustomer(customer);
        accounts.setAccountType("Savings");
        accounts.setBranchAddress("Madrid");
        accounts.setCommunicationSw(false);
        accounts = accountsRepository.save(accounts);
        Long id = accounts.getAccountNumber();
        Customer sharedCustomer = customer;
        transactionTemplate.execute(status -> {
            Accounts loaded = accountsRepository.findById(id).orElseThrow();
            loaded.setBranchAddress("Barcelona");
            return null;
        });

        Accounts afterFirstUpdate = accountsRepository.findById(id).orElseThrow();
        assertThat(afterFirstUpdate.getBranchAddress()).isEqualTo("Barcelona");
        assertThat(afterFirstUpdate.getVersion()).isEqualTo(1L);

        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            Accounts stale = new Accounts();
            stale.setAccountNumber(id);          // same PK as the real row
            stale.setCustomer(sharedCustomer);     // same as the seed (any long is fine)
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
