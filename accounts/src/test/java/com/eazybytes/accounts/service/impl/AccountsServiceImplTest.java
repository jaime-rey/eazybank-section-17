package com.eazybytes.accounts.service.impl;

import com.eazybytes.accounts.dto.AccountsDto;
import com.eazybytes.accounts.dto.AccountsMsgDto;
import com.eazybytes.accounts.dto.CustomerDto;
import com.eazybytes.accounts.entity.Accounts;
import com.eazybytes.accounts.entity.Customer;
import com.eazybytes.accounts.exception.CustomerAlreadyExistsException;
import com.eazybytes.accounts.exception.ResourceNotFoundException;
import com.eazybytes.accounts.mapper.AccountsMapper;
import com.eazybytes.accounts.mapper.CustomerMapper;
import com.eazybytes.accounts.repository.AccountsRepository;
import com.eazybytes.accounts.repository.CustomerRepository;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import com.eazybytes.accounts.dto.CustomerListItemDto;
import com.eazybytes.accounts.dto.CustomerSearchDto;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import com.eazybytes.accounts.dto.CustomerSearchCriteria;
import java.time.LocalDateTime;

import java.util.List;

import static org.assertj.core.api.Assertions.tuple;

@ExtendWith(MockitoExtension.class)
class AccountsServiceImplTest {

    @Mock private AccountsRepository accountsRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private StreamBridge streamBridge;
    @Spy private AccountsMapper accountsMapper = Mappers.getMapper(AccountsMapper.class);
    @Spy private CustomerMapper customerMapper = Mappers.getMapper(CustomerMapper.class);

    @InjectMocks
    private AccountsServiceImpl service;

    // ---------- createAccount ----------

    @Test
    @DisplayName("createAccount: persists customer + account and publishes a communication message via StreamBridge")
    void createAccount_happyPath() {
        CustomerDto dto = createAdaDto();

        when(customerRepository.findByMobileNumber("9345432123")).thenReturn(Optional.empty());
        Customer savedCustomer = new Customer();
        savedCustomer.setCustomerId(42L);
        savedCustomer.setName(dto.getName());
        savedCustomer.setEmail(dto.getEmail());
        savedCustomer.setMobileNumber(dto.getMobileNumber());
        when(customerRepository.save(any(Customer.class))).thenReturn(savedCustomer);
        Accounts savedAccount = new Accounts();
        savedAccount.setAccountNumber(1234567890L);
        savedAccount.setCustomer(savedCustomer);
        when(accountsRepository.save(any(Accounts.class))).thenReturn(savedAccount);

        service.createAccount(dto);

        verify(customerRepository).save(any(Customer.class));
        verify(accountsRepository).save(any(Accounts.class));
        ArgumentCaptor<AccountsMsgDto> msgCaptor = ArgumentCaptor.forClass(AccountsMsgDto.class);
        verify(streamBridge).send(eq("sendCommunication-out-0"), msgCaptor.capture());
        AccountsMsgDto msg = msgCaptor.getValue();
        assertThat(msg.accountNumber()).isEqualTo(1234567890L);
        assertThat(msg.name()).isEqualTo("Ada Lovelace");
        assertThat(msg.email()).isEqualTo("ada@example.com");
        assertThat(msg.mobileNumber()).isEqualTo("9345432123");
    }

    @Test
    @DisplayName("createAccount: throws CustomerAlreadyExistsException when the mobileNumber is already registered")
    void createAccount_customerAlreadyExists() {
        CustomerDto dto = new CustomerDto();
        dto.setMobileNumber("9345432123");
        when(customerRepository.findByMobileNumber("9345432123"))
                .thenReturn(Optional.of(new Customer()));

        assertThatThrownBy(() -> service.createAccount(dto))
                .isInstanceOf(CustomerAlreadyExistsException.class)
                .hasMessageContaining("9345432123");

        verify(customerRepository, never()).save(any());
        verify(accountsRepository, never()).save(any());
        verify(streamBridge, never()).send(any(), any());
    }

    // ---------- fetchAccount ----------

    @Test
    @DisplayName("fetchAccount: returns CustomerDto with AccountsDto populated when customer + account exist")
    void fetchAccount_happyPath() {
        Customer customer = new Customer();
        customer.setCustomerId(42L);
        customer.setName("Ada");
        customer.setEmail("ada@example.com");
        customer.setMobileNumber("9345432123");
        Accounts account = new Accounts();
        account.setAccountNumber(1234567890L);
        account.setAccountType("Savings");
        account.setBranchAddress("123 Main St");
        account.setCustomer(customer);

        when(customerRepository.findByMobileNumber("9345432123")).thenReturn(Optional.of(customer));
        when(accountsRepository.findByCustomer_CustomerId(42L)).thenReturn(Optional.of(account));

        CustomerDto result = service.fetchAccount("9345432123");

        assertThat(result.getName()).isEqualTo("Ada");
        assertThat(result.getEmail()).isEqualTo("ada@example.com");
        assertThat(result.getMobileNumber()).isEqualTo("9345432123");
        assertThat(result.getAccountsDto()).isNotNull();
        assertThat(result.getAccountsDto().getAccountNumber()).isEqualTo(1234567890L);
        assertThat(result.getAccountsDto().getAccountType()).isEqualTo("Savings");
        assertThat(result.getAccountsDto().getBranchAddress()).isEqualTo("123 Main St");
    }

    @Test
    @DisplayName("fetchAccount: throws ResourceNotFoundException when the customer does not exist")
    void fetchAccount_customerNotFound() {
        when(customerRepository.findByMobileNumber("0000000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fetchAccount("0000000000"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer")
                .hasMessageContaining("mobileNumber")
                .hasMessageContaining("0000000000");
    }

    @Test
    @DisplayName("fetchAccount: throws ResourceNotFoundException when the customer exists but has no account")
    void fetchAccount_accountNotFound() {
        Customer customer = new Customer();
        customer.setCustomerId(42L);
        when(customerRepository.findByMobileNumber("9345432123")).thenReturn(Optional.of(customer));
        when(accountsRepository.findByCustomer_CustomerId(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fetchAccount("9345432123"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account")
                .hasMessageContaining("customerId")
                .hasMessageContaining("42");
    }

    // ---------- updateAccount ----------

    @Test
    @DisplayName("updateAccount: returns true and persists account + customer when accountsDto is present")
    void updateAccount_happyPath() {
        AccountsDto accountsDto = new AccountsDto();
        accountsDto.setAccountNumber(1234567890L);
        accountsDto.setAccountType("Checking");
        accountsDto.setBranchAddress("456 Second St");
        CustomerDto customerDto = createAdaDto();
        customerDto.setAccountsDto(accountsDto);

        Customer existingCustomer = new Customer();
        existingCustomer.setCustomerId(42L);
        Accounts existingAccount = new Accounts();
        existingAccount.setAccountNumber(1234567890L);
        existingAccount.setCustomer(existingCustomer);

        when(accountsRepository.findById(1234567890L)).thenReturn(Optional.of(existingAccount));
        when(accountsRepository.save(any(Accounts.class))).thenReturn(existingAccount);
        when(customerRepository.save(any(Customer.class))).thenReturn(existingCustomer);

        boolean updated = service.updateAccount(customerDto);

        assertThat(updated).isTrue();
        verify(accountsRepository).save(any(Accounts.class));
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    @DisplayName("updateAccount: returns false without touching repositories when accountsDto is null")
    void updateAccount_nullAccountsDto_returnsFalseNoOp() {
        CustomerDto customerDto = new CustomerDto();
        customerDto.setAccountsDto(null);

        boolean updated = service.updateAccount(customerDto);

        assertThat(updated).isFalse();
        verify(accountsRepository, never()).findById(any());
        verify(accountsRepository, never()).save(any());
        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateAccount: throws ResourceNotFoundException when the account does not exist")
    void updateAccount_accountNotFound() {
        AccountsDto accountsDto = new AccountsDto();
        accountsDto.setAccountNumber(9999L);
        CustomerDto customerDto = new CustomerDto();
        customerDto.setAccountsDto(accountsDto);
        when(accountsRepository.findById(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateAccount(customerDto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account")
                .hasMessageContaining("9999");
    }

    // ---------- deleteAccount ----------

    @Test
    @DisplayName("deleteAccount: deletes account + customer and returns true")
    void deleteAccount_happyPath() {
        Customer customer = new Customer();
        customer.setCustomerId(42L);
        when(customerRepository.findByMobileNumber("9345432123")).thenReturn(Optional.of(customer));

        boolean deleted = service.deleteAccount("9345432123");

        assertThat(deleted).isTrue();
        verify(accountsRepository).deleteByCustomer_CustomerId(42L);
        verify(customerRepository).deleteById(42L);
    }

    @Test
    @DisplayName("deleteAccount: throws ResourceNotFoundException when the customer does not exist")
    void deleteAccount_customerNotFound() {
        when(customerRepository.findByMobileNumber("0000000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteAccount("0000000000"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer")
                .hasMessageContaining("0000000000");

        verify(accountsRepository, never()).deleteByCustomer_CustomerId(any());
        verify(customerRepository, never()).deleteById(any());
    }

    // ---------- updateCommunicationStatus ----------

    @Test
    @DisplayName("updateCommunicationStatus: sets communicationSw to true, persists and returns true")
    void updateCommunicationStatus_happyPath() {
        Accounts existingAccount = new Accounts();
        existingAccount.setAccountNumber(1234567890L);
        when(accountsRepository.findById(1234567890L)).thenReturn(Optional.of(existingAccount));
        when(accountsRepository.save(any(Accounts.class))).thenReturn(existingAccount);

        boolean updated = service.updateCommunicationStatus(1234567890L);

        assertThat(updated).isTrue();
        assertThat(existingAccount.getCommunicationSw()).isTrue();
        verify(accountsRepository).save(existingAccount);
    }

    @Test
    @DisplayName("updateCommunicationStatus: returns false and does not touch the repository when accountNumber is null")
    void updateCommunicationStatus_nullAccountNumber_returnsFalseNoOp() {
        boolean updated = service.updateCommunicationStatus(null);

        assertThat(updated).isFalse();
        verify(accountsRepository, never()).findById(any());
        verify(accountsRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateCommunicationStatus: throws ResourceNotFoundException when the account is not found")
    void updateCommunicationStatus_accountNotFound() {
        when(accountsRepository.findById(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCommunicationStatus(9999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account")
                .hasMessageContaining("9999");
    }


// ---------- sendCommunication branches ----------

    @Test
    @DisplayName("createAccount: logs INFO when streamBridge.send() returns true")
    void createAccount_communicationPublishSuccess_logsInfo() {
        Logger serviceLogger = (Logger) LoggerFactory.getLogger(AccountsServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        serviceLogger.addAppender(appender);

        try {
            CustomerDto dto = createAdaDto();

            when(customerRepository.findByMobileNumber("9345432123")).thenReturn(Optional.empty());
            Customer savedCustomer = new Customer();
            savedCustomer.setCustomerId(42L);
            when(customerRepository.save(any(Customer.class))).thenReturn(savedCustomer);
            Accounts savedAccount = new Accounts();
            savedAccount.setAccountNumber(1234567890L);
            savedAccount.setCustomer(savedCustomer);
            when(accountsRepository.save(any(Accounts.class))).thenReturn(savedAccount);

            when(streamBridge.send(eq("sendCommunication-out-0"), any(AccountsMsgDto.class)))
                .thenReturn(true);

            service.createAccount(dto);

            boolean logged = appender.list.stream().anyMatch(e ->
                e.getLevel() == Level.INFO
                    && e.getFormattedMessage().contains("Communication event published")
                    && e.getFormattedMessage().contains("1234567890"));
            assertThat(logged).isTrue();
        } finally {
            serviceLogger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("createAccount: logs WARN when streamBridge.send() returns false")
    void createAccount_communicationPublishFailure_logsWarn() {
        // Attach a ListAppender to capture logs emitted by AccountsServiceImpl
        Logger serviceLogger = (Logger) LoggerFactory.getLogger(AccountsServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        serviceLogger.addAppender(appender);

        try {
            // arrange
            CustomerDto dto = createAdaDto();

            when(customerRepository.findByMobileNumber("9345432123")).thenReturn(Optional.empty());

            Customer savedCustomer = new Customer();
            savedCustomer.setCustomerId(42L);
            when(customerRepository.save(any(Customer.class))).thenReturn(savedCustomer);

            Accounts savedAccount = new Accounts();
            savedAccount.setAccountNumber(1234567890L);
            savedAccount.setCustomer(savedCustomer);
            when(accountsRepository.save(any(Accounts.class))).thenReturn(savedAccount);

            when(streamBridge.send(eq("sendCommunication-out-0"), any(AccountsMsgDto.class)))
                .thenReturn(false);

            // act
            service.createAccount(dto);

            // assert: a WARN with the expected text was recorded
            boolean warned = appender.list.stream().anyMatch(e ->
                e.getLevel() == Level.WARN
                    && e.getFormattedMessage().contains("Communication event NOT published")
                    && e.getFormattedMessage().contains("1234567890"));
            assertThat(warned).isTrue();
        } finally {
            serviceLogger.detachAppender(appender);
        }
    }


// ---------- searchCustomers ----------

    @Test
    @DisplayName("searchCustomers: delegates to repository and maps each Customer to a CustomerListItemDto")
    void searchCustomers_mapsPageToDto() {
        // arrange
        CustomerSearchDto filters = new CustomerSearchDto("Ada", null, "934", null);
        Pageable pageable = PageRequest.of(0, 10);

        Customer c1 = new Customer();
        c1.setCustomerId(1L);
        c1.setName("Ada Lovelace");
        c1.setEmail("ada@example.com");
        c1.setMobileNumber("9345432123");

        Customer c2 = new Customer();
        c2.setCustomerId(2L);
        c2.setName("Grace Hopper");
        c2.setEmail("grace@example.com");
        c2.setMobileNumber("9345432124");

        Page<Customer> repoPage = new PageImpl<>(List.of(c1, c2), pageable, 2);
        when(customerRepository.findAll(
            ArgumentMatchers.<Specification<Customer>>any(),
            eq(pageable))).thenReturn(repoPage);

        // act
        Page<CustomerListItemDto> result = service.searchCustomers(filters, pageable);

        // assert
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
            .extracting(CustomerListItemDto::customerId,
                CustomerListItemDto::name,
                CustomerListItemDto::email,
                CustomerListItemDto::mobileNumber)
            .containsExactly(
                tuple(1L, "Ada Lovelace", "ada@example.com", "9345432123"),
                tuple(2L, "Grace Hopper", "grace@example.com", "9345432124"));

        verify(customerRepository).findAll(
            ArgumentMatchers.<Specification<Customer>>any(),
            eq(pageable));
    }

    @Test
    @DisplayName("searchCustomersV2: delegates to repository with built spec and maps to CustomerListItemDto")
    void searchCustomersV2_mapsPageToDto() {
        // arrange
        CustomerSearchCriteria criteria = new CustomerSearchCriteria(
            "Ada", null, null,
            LocalDateTime.now().minusDays(30), null,
            null, null, Boolean.TRUE, null, null);
        Pageable pageable = PageRequest.of(0, 5);

        Customer c1 = new Customer();
        c1.setCustomerId(10L);
        c1.setName("Ada Lovelace");
        c1.setEmail("ada@example.com");
        c1.setMobileNumber("9345432123");

        Page<Customer> repoPage = new PageImpl<>(List.of(c1), pageable, 1);
        when(customerRepository.findAll(
            ArgumentMatchers.<Specification<Customer>>any(),
            eq(pageable))).thenReturn(repoPage);

        // act
        Page<CustomerListItemDto> result = service.searchCustomersV2(criteria, pageable);

        // assert
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent())
            .singleElement()
            .satisfies(item -> {
                assertThat(item.customerId()).isEqualTo(10L);
                assertThat(item.name()).isEqualTo("Ada Lovelace");
                assertThat(item.email()).isEqualTo("ada@example.com");
                assertThat(item.mobileNumber()).isEqualTo("9345432123");
            });

        verify(customerRepository).findAll(
            ArgumentMatchers.<Specification<Customer>>any(),
            eq(pageable));
    }

    private static @NonNull CustomerDto createAdaDto() {
        CustomerDto dto = new CustomerDto();
        dto.setName("Ada Lovelace");
        dto.setEmail("ada@example.com");
        dto.setMobileNumber("9345432123");
        return dto;
    }
}
