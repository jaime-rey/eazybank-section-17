package com.eazybytes.accounts.repository.spec;

import com.eazybytes.accounts.audit.AuditAwareImpl;
import com.eazybytes.accounts.config.JpaAuditingConfig;
import com.eazybytes.accounts.dto.CustomerSearchCriteria;
import com.eazybytes.accounts.entity.Accounts;
import com.eazybytes.accounts.entity.Customer;
import com.eazybytes.accounts.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({JpaAuditingConfig.class, AuditAwareImpl.class})
class CustomerSpecificationsV2Test {

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void build_composesJoinAndSubqueryFilters() {
        customerRepository.save(customer("Alice Savings", "alice@x.com", "5550000001",
            account("Savings"), account("Savings")));
        customerRepository.save(customer("Bob Checking",  "bob@x.com",   "5550000002",
            account("Checking"), account("Checking")));
        customerRepository.save(customer("Carol One",     "carol@x.com", "5550000003",
            account("Savings")));
        customerRepository.save(customer("Dan Orphan",    "dan@x.com",   "5550000004"));

        CustomerSearchCriteria criteria = new CustomerSearchCriteria(
            null, null, null,
            null, null, null,
            2L,           // minAccounts
            null,
            "Savings",    // accountType
            null
        );

        Specification<Customer> spec = CustomerSpecifications.build(criteria);
        List<Customer> result = customerRepository.findAll(spec);

        assertThat(result)
            .hasSize(1)
            .extracting(Customer::getName)
            .containsExactly("Alice Savings");
    }

    @Test
    void build_emptyCriteria_returnsAllCustomers() {
        customerRepository.save(customer("Alice", "a@x.com", "5550000001"));
        customerRepository.save(customer("Bob",   "b@x.com", "5550000002"));
        customerRepository.save(customer("Carol", "c@x.com", "5550000003"));

        CustomerSearchCriteria emptyCriteria = new CustomerSearchCriteria(
            null, null, null, null, null, null, null, null, null, null);

        List<Customer> result = customerRepository.findAll(CustomerSpecifications.build(emptyCriteria));

        assertThat(result).hasSize(3);
    }

    @Test
    void hasAccounts_false_returnsOnlyOrphans() {
        customerRepository.save(customer("Alice", "a@x.com", "5550000001", account("Savings")));
        customerRepository.save(customer("Bob",   "b@x.com", "5550000002"));
        customerRepository.save(customer("Carol", "c@x.com", "5550000003"));

        CustomerSearchCriteria criteria = new CustomerSearchCriteria(
            null, null, null, null, null, null, null,
            false,   // hasAccounts
            null, null);

        List<Customer> result = customerRepository.findAll(CustomerSpecifications.build(criteria));

        assertThat(result)
            .hasSize(2)
            .extracting(Customer::getName)
            .containsExactlyInAnyOrder("Bob", "Carol");
    }

    @Test
    void createdAfter_isEvaluatedAgainstAuditingTimestamp() {
        customerRepository.save(customer("Alice", "a@x.com", "5550000001"));
        customerRepository.save(customer("Bob",   "b@x.com", "5550000002"));

        CustomerSearchCriteria pastCutoff = new CustomerSearchCriteria(
            null, null, null,
            LocalDateTime.now().minusHours(1),   // createdAfter
            null, null, null, null, null, null);
        assertThat(customerRepository.findAll(CustomerSpecifications.build(pastCutoff))).hasSize(2);

        CustomerSearchCriteria futureCutoff = new CustomerSearchCriteria(
            null, null, null,
            LocalDateTime.now().plusHours(1),
            null, null, null, null, null, null);
        assertThat(customerRepository.findAll(CustomerSpecifications.build(futureCutoff))).isEmpty();
    }

    @Test
    void build_composesNameAndEmailWithAnd() {
        customerRepository.save(customer("Alice Morgan", "alice@gmail.com", "5550000001"));
        customerRepository.save(customer("Alice Keys",   "alice@yahoo.com", "5550000002"));
        customerRepository.save(customer("Alan Turing",  "alan@gmail.com",  "5550000003"));

        CustomerSearchCriteria criteria = new CustomerSearchCriteria(
            "alice", "gmail", null, null, null, null, null, null, null, null);

        List<Customer> result = customerRepository.findAll(CustomerSpecifications.build(criteria));

        assertThat(result)
            .hasSize(1)
            .extracting(Customer::getName)
            .containsExactly("Alice Morgan");
    }

    private Customer customer(String name, String email, String mobile) {
        Customer c = new Customer();
        c.setName(name);
        c.setEmail(email);
        c.setMobileNumber(mobile);
        return c;
    }

    private Customer customer(String name, String email, String mobile, Accounts... accts) {
        Customer c = customer(name, email, mobile);
        for (Accounts a : accts) c.addAccount(a);
        return c;
    }

    private Accounts account(String type) {
        Accounts a = new Accounts();
        a.setAccountType(type);
        a.setBranchAddress("Test branch");
        a.setCommunicationSw(false);
        return a;
    }
}
