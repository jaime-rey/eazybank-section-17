package com.eazybytes.accounts.service.impl;

import com.eazybytes.accounts.constants.AccountsConstants;
import com.eazybytes.accounts.dto.AccountsDto;
import com.eazybytes.accounts.dto.AccountsMsgDto;
import com.eazybytes.accounts.dto.CustomerDto;
import com.eazybytes.accounts.dto.CustomerListItemDto;
import com.eazybytes.accounts.entity.Accounts;
import com.eazybytes.accounts.entity.Customer;
import com.eazybytes.accounts.exception.CustomerAlreadyExistsException;
import com.eazybytes.accounts.exception.ResourceNotFoundException;
import com.eazybytes.accounts.mapper.AccountsMapper;
import com.eazybytes.accounts.mapper.CustomerMapper;
import com.eazybytes.accounts.repository.AccountsRepository;
import com.eazybytes.accounts.repository.CustomerRepository;
import com.eazybytes.accounts.service.IAccountsService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.eazybytes.accounts.dto.CustomerSearchDto;
import com.eazybytes.accounts.repository.spec.CustomerSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import java.util.Optional;

@Service
@AllArgsConstructor
public class AccountsServiceImpl  implements IAccountsService {

    private static final Logger log = LoggerFactory.getLogger(AccountsServiceImpl.class);

    private AccountsRepository accountsRepository;
    private CustomerRepository customerRepository;
    private final StreamBridge streamBridge;
    private final AccountsMapper accountsMapper;
    private final CustomerMapper customerMapper;

    /**
     * @param customerDto - CustomerDto Object
     */
    @Override
    public void createAccount(CustomerDto customerDto) {
        log.info("createAccount start, mobileNumber={}", customerDto.getMobileNumber());
        Customer customer = customerMapper.toEntity(customerDto);
        Optional<Customer> optionalCustomer = customerRepository.findByMobileNumber(customerDto.getMobileNumber());
        if(optionalCustomer.isPresent()) {
            log.warn("createAccount rejected, mobileNumber already exists mobileNumber={}", customerDto.getMobileNumber());
            throw new CustomerAlreadyExistsException("Customer already registered with given mobileNumber "
                    +customerDto.getMobileNumber());
        }
        Customer savedCustomer = customerRepository.save(customer);
        Accounts savedAccount = accountsRepository.save(createNewAccount(savedCustomer));
        log.info("createAccount success, customerId={} accountNumber={}",
                savedCustomer.getCustomerId(), savedAccount.getAccountNumber());
        sendCommunication(savedAccount, savedCustomer);
    }

    private void sendCommunication(Accounts account, Customer customer) {
        var accountsMsgDto = new AccountsMsgDto(account.getAccountNumber(), customer.getName(),
                customer.getEmail(), customer.getMobileNumber());
        log.info("Publishing communication event, accountNumber={} mobileNumber={}",
                account.getAccountNumber(), customer.getMobileNumber());
        var result = streamBridge.send("sendCommunication-out-0", accountsMsgDto);
        if (result) {
            log.info("Communication event published, accountNumber={}", account.getAccountNumber());
        } else {
            log.warn("Communication event NOT published, accountNumber={}", account.getAccountNumber());
        }
    }

    /**
     * @param customer - Customer Object
     * @return the new account details
     */
    private Accounts createNewAccount(Customer customer) {
        Accounts newAccount = new Accounts();
        customer.addAccount(newAccount);
        newAccount.setAccountType(AccountsConstants.SAVINGS);
        newAccount.setBranchAddress(AccountsConstants.ADDRESS);
        return newAccount;
    }

    /**
     * @param mobileNumber - Input Mobile Number
     * @return Accounts Details based on a given mobileNumber
     */
    @Override
    public CustomerDto fetchAccount(String mobileNumber) {
        log.info("fetchAccount start, mobileNumber={}", mobileNumber);
        Customer customer = customerRepository.findByMobileNumber(mobileNumber).orElseThrow(
                () -> new ResourceNotFoundException("Customer", "mobileNumber", mobileNumber)
        );
        Accounts accounts = accountsRepository.findByCustomer_CustomerId(customer.getCustomerId()).orElseThrow(
                () -> new ResourceNotFoundException("Account", "customerId", customer.getCustomerId().toString())
        );
        CustomerDto customerDto = customerMapper.toDto(customer);
        customerDto.setAccountsDto(accountsMapper.toDto(accounts));
        log.debug("fetchAccount success, customerId={} accountNumber={}",
                customer.getCustomerId(), accounts.getAccountNumber());
        return customerDto;
    }

    /**
     * @param customerDto - CustomerDto Object
     * @return boolean indicating if the update of Account details is successful or not
     */
    @Override
    @Transactional
    public boolean updateAccount(CustomerDto customerDto) {
        boolean isUpdated = false;
        AccountsDto accountsDto = customerDto.getAccountsDto();
        if(accountsDto !=null ){
            log.info("updateAccount start, accountNumber={}", accountsDto.getAccountNumber());
            Accounts accounts = accountsRepository.findById(accountsDto.getAccountNumber()).orElseThrow(
                    () -> new ResourceNotFoundException("Account", "AccountNumber", accountsDto.getAccountNumber().toString())
            );
            accountsMapper.updateEntity(accountsDto, accounts);
            accounts = accountsRepository.save(accounts);

            Customer customer = accounts.getCustomer();
            customerMapper.updateEntity(customerDto, customer);
            customerRepository.save(customer);
            isUpdated = true;
            log.info("updateAccount success, accountNumber={} customerId={}",
                    accounts.getAccountNumber(), customer.getCustomerId());
        } else {
            log.warn("updateAccount called with null accountsDto");
        }
        return  isUpdated;
    }

    /**
     * @param mobileNumber - Input Mobile Number
     * @return boolean indicating if the delete of Account details is successful or not
     */
    @Override
    public boolean deleteAccount(String mobileNumber) {
        log.info("deleteAccount start, mobileNumber={}", mobileNumber);
        Customer customer = customerRepository.findByMobileNumber(mobileNumber).orElseThrow(
                () -> new ResourceNotFoundException("Customer", "mobileNumber", mobileNumber)
        );
        accountsRepository.deleteByCustomer_CustomerId(customer.getCustomerId());
        customerRepository.deleteById(customer.getCustomerId());
        log.info("deleteAccount success, customerId={}", customer.getCustomerId());
        return true;
    }

    /**
     * @param accountNumber - Long
     * @return boolean indicating if the update of communication status is successful or not
     */
    @Override
    public boolean updateCommunicationStatus(Long accountNumber) {
        boolean isUpdated = false;
        if(accountNumber !=null ){
            log.info("updateCommunicationStatus start, accountNumber={}", accountNumber);
            Accounts accounts = accountsRepository.findById(accountNumber).orElseThrow(
                    () -> new ResourceNotFoundException("Account", "AccountNumber", accountNumber.toString())
            );
            accounts.setCommunicationSw(true);
            accountsRepository.save(accounts);
            isUpdated = true;
            log.info("updateCommunicationStatus success, accountNumber={}", accountNumber);
        } else {
            log.warn("updateCommunicationStatus called with null accountNumber");
        }
        return  isUpdated;
    }

    @Override
    public Page<CustomerListItemDto> searchCustomers(CustomerSearchDto filters, Pageable pageable) {
        Specification<Customer> spec = CustomerSpecifications.build(
            filters.name(),
            filters.email(),
            filters.mobileNumberPrefix(),
            filters.hasAccount()
        );
        return customerRepository.findAll(spec, pageable)
            .map(c -> new CustomerListItemDto(
                c.getCustomerId(),
                c.getName(),
                c.getEmail(),
                c.getMobileNumber()));
    }

}
