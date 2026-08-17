package com.eazybytes.accounts.service.impl;

import com.eazybytes.accounts.dto.AccountsDto;
import com.eazybytes.accounts.dto.AccountsMsgDto;
import com.eazybytes.accounts.dto.CustomerDto;
import com.eazybytes.accounts.entity.Accounts;
import com.eazybytes.accounts.entity.Customer;
import com.eazybytes.accounts.exception.CustomerAlreadyExistsException;
import com.eazybytes.accounts.exception.ResourceNotFoundException;
import com.eazybytes.accounts.repository.AccountsRepository;
import com.eazybytes.accounts.repository.CustomerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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

@ExtendWith(MockitoExtension.class)
class AccountsServiceImplTest {

    @Mock private AccountsRepository accountsRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private StreamBridge streamBridge;

    @InjectMocks
    private AccountsServiceImpl service;

    // ---------- createAccount ----------

    @Test
    @DisplayName("createAccount: persists customer + account and publishes a communication message via StreamBridge")
    void createAccount_happyPath() {
        CustomerDto dto = new CustomerDto();
        dto.setName("Ada Lovelace");
        dto.setEmail("ada@example.com");
        dto.setMobileNumber("9345432123");

        when(customerRepository.findByMobileNumber("9345432123")).thenReturn(Optional.empty());
        Customer savedCustomer = new Customer();
        savedCustomer.setCustomerId(42L);
        savedCustomer.setName(dto.getName());
        savedCustomer.setEmail(dto.getEmail());
        savedCustomer.setMobileNumber(dto.getMobileNumber());
        when(customerRepository.save(any(Customer.class))).thenReturn(savedCustomer);
        Accounts savedAccount = new Accounts();
        savedAccount.setAccountNumber(1234567890L);
        savedAccount.setCustomerId(42L);
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
        account.setCustomerId(42L);

        when(customerRepository.findByMobileNumber("9345432123")).thenReturn(Optional.of(customer));
        when(accountsRepository.findByCustomerId(42L)).thenReturn(Optional.of(account));

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
        when(accountsRepository.findByCustomerId(42L)).thenReturn(Optional.empty());

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
        CustomerDto customerDto = new CustomerDto();
        customerDto.setName("Ada Lovelace");
        customerDto.setEmail("ada@example.com");
        customerDto.setMobileNumber("9345432123");
        customerDto.setAccountsDto(accountsDto);

        Accounts existingAccount = new Accounts();
        existingAccount.setAccountNumber(1234567890L);
        existingAccount.setCustomerId(42L);
        Customer existingCustomer = new Customer();
        existingCustomer.setCustomerId(42L);

        when(accountsRepository.findById(1234567890L)).thenReturn(Optional.of(existingAccount));
        when(accountsRepository.save(any(Accounts.class))).thenReturn(existingAccount);
        when(customerRepository.findById(42L)).thenReturn(Optional.of(existingCustomer));
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

    @Test
    @DisplayName("updateAccount: throws ResourceNotFoundException when the linked customer is not found")
    void updateAccount_customerNotFound() {
        AccountsDto accountsDto = new AccountsDto();
        accountsDto.setAccountNumber(1234567890L);
        CustomerDto customerDto = new CustomerDto();
        customerDto.setAccountsDto(accountsDto);

        Accounts existingAccount = new Accounts();
        existingAccount.setAccountNumber(1234567890L);
        existingAccount.setCustomerId(42L);
        when(accountsRepository.findById(1234567890L)).thenReturn(Optional.of(existingAccount));
        when(accountsRepository.save(any(Accounts.class))).thenReturn(existingAccount);
        when(customerRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateAccount(customerDto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer")
                .hasMessageContaining("42");
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
        verify(accountsRepository).deleteByCustomerId(42L);
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

        verify(accountsRepository, never()).deleteByCustomerId(any());
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
}
