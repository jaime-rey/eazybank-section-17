package com.eazybytes.accounts;

import com.eazybytes.accounts.repository.CustomerRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import com.eazybytes.accounts.dto.AccountsDto;
import com.eazybytes.accounts.dto.CustomerDetailsDto;
import com.eazybytes.accounts.dto.CustomerDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.test.context.TestConstructor.AutowireMode.ALL;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("feigntest")
@TestConstructor(autowireMode = ALL)
@RequiredArgsConstructor
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class CustomerDetailsIT {

    private static WireMockServer cardsMock;
    private static WireMockServer loansMock;

    private final TestRestTemplate restTemplate;
    private final CustomerRepository customerRepository;

    @BeforeAll
    static void startMocks() {
        cardsMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        loansMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        cardsMock.start();
        loansMock.start();
        System.setProperty("accounts.services.cards.url", "http://localhost:" + cardsMock.port());
        System.setProperty("accounts.services.loans.url", "http://localhost:" + loansMock.port());
    }

    @AfterAll
    static void stopMocks() {
        if (cardsMock != null) cardsMock.stop();
        if (loansMock != null) loansMock.stop();
        System.clearProperty("accounts.services.cards.url");
        System.clearProperty("accounts.services.loans.url");
    }

    @BeforeEach
    void resetMocks() {
        cardsMock.resetAll();
        loansMock.resetAll();
    }

    @Test
    void fetchCustomerDetails_aggregatesAccountCardsAndLoans() {
        // 1. Seed DB via HTTP
        String mobileNumber = "9998887777";
        String correlationId = "test-corr-id-123";
        CustomerDto customerDto = new CustomerDto();
        customerDto.setName("Ada Lovelace");
        customerDto.setEmail("ada@example.com");
        customerDto.setMobileNumber(mobileNumber);
        AccountsDto accountsDto = new AccountsDto();
        accountsDto.setAccountType("Savings");
        accountsDto.setBranchAddress("123 Main Street");
        customerDto.setAccountsDto(accountsDto);
        ResponseEntity<Void> createResponse =
            restTemplate.postForEntity("/api/create", customerDto, Void.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 2. Stub cards downstream
        cardsMock.stubFor(get(urlPathEqualTo("/api/fetch"))
            .withQueryParam("mobileNumber", equalTo(mobileNumber))
            .willReturn(okJson("""
            {
              "mobileNumber": "%s",
              "cardNumber": "100646930341",
              "cardType": "Credit Card",
              "totalLimit": 100000,
              "amountUsed": 1000,
              "availableAmount": 99000
            }
            """.formatted(mobileNumber))));

        // 3. Stub loans downstream
        loansMock.stubFor(get(urlPathEqualTo("/api/fetch"))
            .withQueryParam("mobileNumber", equalTo(mobileNumber))
            .willReturn(okJson("""
            {
              "mobileNumber": "%s",
              "loanNumber": "548732457654",
              "loanType": "Home Loan",
              "totalLoan": 100000,
              "amountPaid": 1000,
              "outstandingAmount": 99000
            }
            """.formatted(mobileNumber))));

        // 4. Call aggregated endpoint with correlation header
        HttpHeaders headers = new HttpHeaders();
        headers.set("eazybank-correlation-id", correlationId);
        ResponseEntity<CustomerDetailsDto> response = restTemplate.exchange(
            "/api/fetchCustomerDetails?mobileNumber=" + mobileNumber,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            CustomerDetailsDto.class);

        // 5. Assert aggregated response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        CustomerDetailsDto body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getName()).isEqualTo("Ada Lovelace");
        assertThat(body.getEmail()).isEqualTo("ada@example.com");
        assertThat(body.getMobileNumber()).isEqualTo(mobileNumber);
        assertThat(body.getAccountsDto()).isNotNull();
        assertThat(body.getAccountsDto().getAccountType()).isEqualTo("Savings");
        assertThat(body.getAccountsDto().getBranchAddress()).isEqualTo("123 Main Street, New York");
        assertThat(body.getCardsDto()).isNotNull();
        assertThat(body.getCardsDto().getCardNumber()).isEqualTo("100646930341");
        assertThat(body.getLoansDto()).isNotNull();
        assertThat(body.getLoansDto().getLoanNumber()).isEqualTo("548732457654");

        // 6. Verify downstream received correlation header
        cardsMock.verify(getRequestedFor(urlPathEqualTo("/api/fetch"))
            .withHeader("eazybank-correlation-id", equalTo(correlationId)));
        loansMock.verify(getRequestedFor(urlPathEqualTo("/api/fetch"))
            .withHeader("eazybank-correlation-id", equalTo(correlationId)));
    }

    @Test
    void fetchCustomerDetails_whenCardsDown_fallsBackWithNullCards() {
        // 1. Seed DB via HTTP (distinct mobile to avoid clash with prior test's data)
        String mobileNumber = "8887776666";
        String correlationId = "test-corr-id-456";
        CustomerDto customerDto = new CustomerDto();
        customerDto.setName("Grace Hopper");
        customerDto.setEmail("grace@example.com");
        customerDto.setMobileNumber(mobileNumber);
        AccountsDto accountsDto = new AccountsDto();
        accountsDto.setAccountType("Savings");
        accountsDto.setBranchAddress("456 Baker Street");
        customerDto.setAccountsDto(accountsDto);
        ResponseEntity<Void> createResponse =
            restTemplate.postForEntity("/api/create", customerDto, Void.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 2. Stub cards to return 500 -> triggers circuit breaker fallback
        cardsMock.stubFor(get(urlPathEqualTo("/api/fetch"))
            .withQueryParam("mobileNumber", equalTo(mobileNumber))
            .willReturn(serverError()));

        // 3. Stub loans normally
        loansMock.stubFor(get(urlPathEqualTo("/api/fetch"))
            .withQueryParam("mobileNumber", equalTo(mobileNumber))
            .willReturn(okJson("""
            {
              "mobileNumber": "%s",
              "loanNumber": "111122223333",
              "loanType": "Personal Loan",
              "totalLoan": 50000,
              "amountPaid": 10000,
              "outstandingAmount": 40000
            }
            """.formatted(mobileNumber))));

        // 4. Call aggregated endpoint
        HttpHeaders headers = new HttpHeaders();
        headers.set("eazybank-correlation-id", correlationId);
        ResponseEntity<CustomerDetailsDto> response = restTemplate.exchange(
            "/api/fetchCustomerDetails?mobileNumber=" + mobileNumber,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            CustomerDetailsDto.class);

        // 5. Aggregation still succeeds; cards is null, loans is present
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        CustomerDetailsDto body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getName()).isEqualTo("Grace Hopper");
        assertThat(body.getAccountsDto()).isNotNull();
        assertThat(body.getLoansDto()).isNotNull();
        assertThat(body.getLoansDto().getLoanNumber()).isEqualTo("111122223333");
        assertThat(body.getCardsDto()).isNull();

        // 6. Verify cards was actually called (fallback fired because of 500, not because we didn't call)
        cardsMock.verify(getRequestedFor(urlPathEqualTo("/api/fetch"))
            .withQueryParam("mobileNumber", equalTo(mobileNumber)));
    }
}
