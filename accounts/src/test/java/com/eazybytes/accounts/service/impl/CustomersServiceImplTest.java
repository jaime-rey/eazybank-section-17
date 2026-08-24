package com.eazybytes.accounts.service.impl;

import com.eazybytes.accounts.dto.CardsDto;
import com.eazybytes.accounts.dto.CustomerDetailsDto;
import com.eazybytes.accounts.dto.LoansDto;
import com.eazybytes.accounts.entity.Accounts;
import com.eazybytes.accounts.entity.Customer;
import com.eazybytes.accounts.exception.ResourceNotFoundException;
import com.eazybytes.accounts.mapper.AccountsMapper;
import com.eazybytes.accounts.mapper.CustomerMapper;
import com.eazybytes.accounts.repository.AccountsRepository;
import com.eazybytes.accounts.repository.CustomerRepository;
import com.eazybytes.accounts.service.client.CardsFeignClient;
import com.eazybytes.accounts.service.client.LoansFeignClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomersServiceImplTest {

    @Mock private AccountsRepository accountsRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private CardsFeignClient cardsFeignClient;
    @Mock private LoansFeignClient loansFeignClient;
    @Spy private AccountsMapper accountsMapper = Mappers.getMapper(AccountsMapper.class);
    @Spy private CustomerMapper customerMapper = Mappers.getMapper(CustomerMapper.class);

    @InjectMocks
    private CustomersServiceImpl service;

    private static final String MOBILE = "9345432123";
    private static final String CORRELATION_ID = "corr-42";

    @Test
    @DisplayName("fetchCustomerDetails: returns CustomerDetailsDto with account, loans and cards populated on happy path")
    void fetchCustomerDetails_happyPath() {
        Customer customer = customer();
        Accounts account = account();
        LoansDto loans = new LoansDto();
        loans.setLoanNumber("100000001");
        CardsDto cards = new CardsDto();
        cards.setCardNumber("1234567890123456");

        when(customerRepository.findByMobileNumber(MOBILE)).thenReturn(Optional.of(customer));
        when(accountsRepository.findByCustomer_CustomerId(42L)).thenReturn(Optional.of(account));
        when(loansFeignClient.fetchLoanDetails(CORRELATION_ID, MOBILE)).thenReturn(ResponseEntity.ok(loans));
        when(cardsFeignClient.fetchCardDetails(CORRELATION_ID, MOBILE)).thenReturn(ResponseEntity.ok(cards));

        CustomerDetailsDto result = service.fetchCustomerDetails(MOBILE, CORRELATION_ID);

        assertThat(result.getName()).isEqualTo("Ada");
        assertThat(result.getEmail()).isEqualTo("ada@example.com");
        assertThat(result.getMobileNumber()).isEqualTo(MOBILE);
        assertThat(result.getAccountsDto()).isNotNull();
        assertThat(result.getAccountsDto().getAccountNumber()).isEqualTo(1234567890L);
        assertThat(result.getLoansDto()).isSameAs(loans);
        assertThat(result.getCardsDto()).isSameAs(cards);
    }

    @Test
    @DisplayName("fetchCustomerDetails: leaves loansDto null when loansFeignClient returns null")
    void fetchCustomerDetails_loansFeignReturnsNull() {
        when(customerRepository.findByMobileNumber(MOBILE)).thenReturn(Optional.of(customer()));
        when(accountsRepository.findByCustomer_CustomerId(42L)).thenReturn(Optional.of(account()));
        when(loansFeignClient.fetchLoanDetails(CORRELATION_ID, MOBILE)).thenReturn(null);
        CardsDto cards = new CardsDto();
        when(cardsFeignClient.fetchCardDetails(CORRELATION_ID, MOBILE)).thenReturn(ResponseEntity.ok(cards));

        CustomerDetailsDto result = service.fetchCustomerDetails(MOBILE, CORRELATION_ID);

        assertThat(result.getLoansDto()).isNull();
        assertThat(result.getCardsDto()).isSameAs(cards);
    }

    @Test
    @DisplayName("fetchCustomerDetails: leaves cardsDto null when cardsFeignClient returns null")
    void fetchCustomerDetails_cardsFeignReturnsNull() {
        when(customerRepository.findByMobileNumber(MOBILE)).thenReturn(Optional.of(customer()));
        when(accountsRepository.findByCustomer_CustomerId(42L)).thenReturn(Optional.of(account()));
        LoansDto loans = new LoansDto();
        when(loansFeignClient.fetchLoanDetails(CORRELATION_ID, MOBILE)).thenReturn(ResponseEntity.ok(loans));
        when(cardsFeignClient.fetchCardDetails(CORRELATION_ID, MOBILE)).thenReturn(null);

        CustomerDetailsDto result = service.fetchCustomerDetails(MOBILE, CORRELATION_ID);

        assertThat(result.getLoansDto()).isSameAs(loans);
        assertThat(result.getCardsDto()).isNull();
    }

    @Test
    @DisplayName("fetchCustomerDetails: throws ResourceNotFoundException when the customer does not exist")
    void fetchCustomerDetails_customerNotFound() {
        when(customerRepository.findByMobileNumber("0000000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fetchCustomerDetails("0000000000", CORRELATION_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer")
                .hasMessageContaining("mobileNumber")
                .hasMessageContaining("0000000000");
    }

    @Test
    @DisplayName("fetchCustomerDetails: throws ResourceNotFoundException when the customer has no account")
    void fetchCustomerDetails_accountNotFound() {
        when(customerRepository.findByMobileNumber(MOBILE)).thenReturn(Optional.of(customer()));
        when(accountsRepository.findByCustomer_CustomerId(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fetchCustomerDetails(MOBILE, CORRELATION_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account")
                .hasMessageContaining("customerId")
                .hasMessageContaining("42");
    }

    // ---------- fixtures ----------

    private static Customer customer() {
        Customer c = new Customer();
        c.setCustomerId(42L);
        c.setName("Ada");
        c.setEmail("ada@example.com");
        c.setMobileNumber(MOBILE);
        return c;
    }

    private static Accounts account() {
        Accounts a = new Accounts();
        a.setCustomer(customer());
        a.setAccountNumber(1234567890L);
        a.setAccountType("Savings");
        a.setBranchAddress("123 Main St");
        return a;
    }
}
