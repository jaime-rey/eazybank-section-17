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
import com.eazybytes.accounts.service.ICustomersService;
import com.eazybytes.accounts.service.client.CardsFeignClient;
import com.eazybytes.accounts.service.client.LoansFeignClient;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;

@Service
@AllArgsConstructor
public class CustomersServiceImpl implements ICustomersService {

    private static final Logger log = LoggerFactory.getLogger(CustomersServiceImpl.class);

    private AccountsRepository accountsRepository;
    private CustomerRepository customerRepository;
    private CardsFeignClient cardsFeignClient;
    private LoansFeignClient loansFeignClient;
    private AccountsMapper accountsMapper;
    private CustomerMapper customerMapper;

    /**
     * @param mobileNumber - Input Mobile Number
     *  @param correlationId - Correlation ID value generated at Edge server
     * @return Customer Details based on a given mobileNumber
     */
    @Override
    public CustomerDetailsDto fetchCustomerDetails(String mobileNumber, String correlationId) {
        log.info("fetchCustomerDetails start, mobileNumber={} correlationId={}", mobileNumber, correlationId);
        Customer customer = customerRepository.findByMobileNumber(mobileNumber).orElseThrow(
                () -> new ResourceNotFoundException("Customer", "mobileNumber", mobileNumber)
        );
        Accounts accounts = accountsRepository.findByCustomer_CustomerId(customer.getCustomerId()).orElseThrow(
                () -> new ResourceNotFoundException("Account", "customerId", customer.getCustomerId().toString())
        );

        CustomerDetailsDto customerDetailsDto = customerMapper.toDetailsDto(customer);
        customerDetailsDto.setAccountsDto(accountsMapper.toDto(accounts));

        log.debug("Calling loans and cards services in parallel, customerId={}", customer.getCustomerId());
        CompletableFuture<ResponseEntity<LoansDto>> loansFuturo = CompletableFuture.supplyAsync(
            () -> loansFeignClient.fetchLoanDetails(correlationId, mobileNumber)
        );

        CompletableFuture<ResponseEntity<CardsDto>> cardsFuturo = CompletableFuture.supplyAsync(
            () -> cardsFeignClient.fetchCardDetails(correlationId, mobileNumber)
        );

        CompletableFuture.allOf(loansFuturo, cardsFuturo).join();

        ResponseEntity<LoansDto> loansDtoResponseEntity  = loansFuturo.join();
        if(null != loansDtoResponseEntity) {
            customerDetailsDto.setLoansDto(loansDtoResponseEntity.getBody());
        } else {
            log.warn("loansFeignClient returned null, fallback triggered mobileNumber={}", mobileNumber);
        }

        ResponseEntity<CardsDto> cardsDtoResponseEntity = cardsFuturo.join();
        if(null != cardsDtoResponseEntity) {
            customerDetailsDto.setCardsDto(cardsDtoResponseEntity.getBody());
        } else {
            log.warn("cardsFeignClient returned null, fallback triggered mobileNumber={}", mobileNumber);
        }

        log.info("fetchCustomerDetails success, customerId={} loansPresent={} cardsPresent={}",
                customer.getCustomerId(),
                customerDetailsDto.getLoansDto() != null,
                customerDetailsDto.getCardsDto() != null);
        return customerDetailsDto;

    }
}
