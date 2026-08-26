package com.eazybytes.accounts.repository.spec;

import com.eazybytes.accounts.entity.Customer;
import com.eazybytes.accounts.entity.Accounts;
import com.eazybytes.accounts.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import com.eazybytes.accounts.audit.AuditAwareImpl;
import com.eazybytes.accounts.config.JpaAuditingConfig;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({JpaAuditingConfig.class, AuditAwareImpl.class})
class CustomerSpecificationsTest {

    @Autowired
    private CustomerRepository customerRepository;

    private Customer newCustomer(String name, String email, String mobile) {
        Customer c = new Customer();
        c.setName(name);
        c.setEmail(email);
        c.setMobileNumber(mobile);
        return c;
    }

    @Test
    void nameContains_isCaseInsensitive() {
        customerRepository.save(newCustomer("Alice Morgan", "alice@example.com", "5550000001"));
        customerRepository.save(newCustomer("BOB ALIBERTI", "bob@example.com", "5550000002"));
        customerRepository.save(newCustomer("Charlie Brown", "charlie@example.com", "5550000003"));

        Specification<Customer> spec = CustomerSpecifications.nameContains("ALI");
        List<Customer> result = customerRepository.findAll(spec);

        assertThat(result)
            .hasSize(2)
            .extracting(Customer::getName)
            .containsExactlyInAnyOrder("Alice Morgan", "BOB ALIBERTI");
    }

    @Test
    void emailContains_isCaseInsensitive() {
        customerRepository.save(newCustomer("Alice", "alice@gmail.com", "5550000001"));
        customerRepository.save(newCustomer("Bob", "bob@GMAIL.COM", "5550000002"));
        customerRepository.save(newCustomer("Charlie", "charlie@yahoo.com", "5550000003"));

        List<Customer> result = customerRepository.findAll(
            CustomerSpecifications.emailContains("GmAiL"));

        assertThat(result)
            .hasSize(2)
            .extracting(Customer::getEmail)
            .containsExactlyInAnyOrder("alice@gmail.com", "bob@GMAIL.COM");
    }

    @Test
    void hasAtLeastOneAccount_filtersOutOrphans() {
        customerRepository.save(newCustomerWithAccount("Alice", "a@x.com", "5550000001"));
        customerRepository.save(newCustomer("Bob", "b@x.com", "5550000002"));  // no account

        List<Customer> result = customerRepository.findAll(
            CustomerSpecifications.hasAtLeastOneAccount());

        assertThat(result)
            .hasSize(1)
            .extracting(Customer::getName)
            .containsExactly("Alice");
    }

    @Test
    void mobileStartsWith_matchesPrefixOnly() {
        customerRepository.save(newCustomer("Alice", "a@x.com", "5551110000"));
        customerRepository.save(newCustomer("Bob",   "b@x.com", "5559990000"));
        customerRepository.save(newCustomer("Charlie","c@x.com", "6665551111"));

        List<Customer> result = customerRepository.findAll(
            CustomerSpecifications.mobileStartsWith("555"));

        assertThat(result)
            .hasSize(2)
            .extracting(Customer::getMobileNumber)
            .containsExactlyInAnyOrder("5551110000", "5559990000");
    }

    @Test
    void build_composesWithAnd() {
        customerRepository.save(newCustomerWithAccount("Alice Morgan", "alice@gmail.com", "5550000001"));
        customerRepository.save(newCustomerWithAccount("Alicia Keys",  "alicia@yahoo.com", "5550000002"));
        customerRepository.save(newCustomer(          "Alan Turing",   "alan@gmail.com", "5550000003"));
        customerRepository.save(newCustomer(          "Bob",           "bob@gmail.com", "6660000004"));

        Specification<Customer> spec = CustomerSpecifications.build(
            "ali",     // name contains
            "gmail",   // email contains
            "555",     // mobile starts with
            true);     // has at least one account

        List<Customer> result = customerRepository.findAll(spec);

        assertThat(result)
            .hasSize(1)
            .extracting(Customer::getName)
            .containsExactly("Alice Morgan");
    }

    @Test
    void build_ignoresNullAndBlankFilters() {
        customerRepository.save(newCustomer("Alice", "a@x.com", "5550000001"));
        customerRepository.save(newCustomer("Bob",   "b@x.com", "5550000002"));
        customerRepository.save(newCustomer("Charlie","c@x.com", "5550000003"));

        Specification<Customer> spec = CustomerSpecifications.build(
            null, "", "   ", null);

        List<Customer> result = customerRepository.findAll(spec);

        assertThat(result).hasSize(3);
    }

    // -- Helper method to create a customer with accounts
    private Customer newCustomerWithAccount(String name, String email, String mobile) {
        Customer c = newCustomer(name, email, mobile);
        Accounts a = new Accounts();
        a.setAccountType("Savings");
        a.setBranchAddress("123 Main St");
        a.setCommunicationSw(false);
        c.addAccount(a);
        return c;
    }
}
