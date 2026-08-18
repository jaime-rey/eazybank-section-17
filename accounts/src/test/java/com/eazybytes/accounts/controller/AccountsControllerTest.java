package com.eazybytes.accounts.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import com.eazybytes.accounts.constants.AccountsConstants;
import com.eazybytes.accounts.dto.AccountsContactInfoDto;
import com.eazybytes.accounts.dto.CustomerDto;
import com.eazybytes.accounts.exception.CustomerAlreadyExistsException;
import com.eazybytes.accounts.exception.ResourceNotFoundException;
import com.eazybytes.accounts.service.IAccountsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@WebMvcTest(AccountsController.class)
@TestPropertySource(properties = {
        "build.version=test-1.2.3",
        "JAVA_HOME=/opt/java/test-21"
})
class AccountsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IAccountsService iAccountsService;

    @MockitoBean
    private AccountsContactInfoDto accountsContactInfoDto;

    @Test
    @DisplayName("GET /api/contact-info returns 200 with the injected AccountsContactInfoDto payload")
    void getContactInfo_returnsDto() throws Exception {

        when(accountsContactInfoDto.getMessage())
                .thenReturn("Welcome to EazyBank accounts service");

        when(accountsContactInfoDto.getContactDetails()).thenReturn(Map.of(
                "name", "Ada Lovelace",
                "email", "ada@eazybank.com"));

        when(accountsContactInfoDto.getOnCallSupport()).thenReturn(List.of(
                "+1 555 111 2233",
                "+1 555 444 5566"));

        mockMvc.perform(get("/api/contact-info"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Welcome to EazyBank accounts service"))
                .andExpect(jsonPath("$.contactDetails.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.contactDetails.email").value("ada@eazybank.com"))
                .andExpect(jsonPath("$.onCallSupport", hasSize(2)))
                .andExpect(jsonPath("$.onCallSupport[0]").value("+1 555 111 2233"))
                .andExpect(jsonPath("$.onCallSupport[1]").value("+1 555 444 5566"));
    }

    @Test
    @DisplayName("POST /api/create returns 201 and passes the deserialized CustomerDto to the service")
    void createAccount_happyPath() throws Exception {
        CustomerDto dto = new CustomerDto();
        dto.setName("Ada Lovelace");
        dto.setEmail("ada@example.com");
        dto.setMobileNumber("9345432123");

        mockMvc.perform(post("/api/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusCode").value(AccountsConstants.STATUS_201))
                .andExpect(jsonPath("$.statusMsg").value(AccountsConstants.MESSAGE_201));

        ArgumentCaptor<CustomerDto> captor = ArgumentCaptor.forClass(CustomerDto.class);
        verify(iAccountsService).createAccount(captor.capture());
        CustomerDto received = captor.getValue();

        assertThat(received.getName()).isEqualTo("Ada Lovelace");
        assertThat(received.getEmail()).isEqualTo("ada@example.com");
        assertThat(received.getMobileNumber()).isEqualTo("9345432123");

    }

    @Test
    @DisplayName("GET /api/fetch with an invalid mobileNumber returns 400 with per-param validation errors")
    void fetchAccountDetails_invalidMobileNumber_returns400() throws Exception {

        mockMvc.perform(get("/api/fetch")
                .param("mobileNumber", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mobileNumber").exists())
                .andExpect(jsonPath("$.mobileNumber").value("Mobile number must be 10 digits"));

        verify(iAccountsService, never()).fetchAccount(anyString());
    }

    @Test
    @DisplayName("GET /api/fetch returns 200 with the CustomerDto returned by the service")
    void fetchAccountDetails_happyPath() throws Exception {
        CustomerDto dto = new CustomerDto();
        dto.setName("Ada Lovelace");
        dto.setEmail("ada@example.com");
        dto.setMobileNumber("9345432123");

        when(iAccountsService.fetchAccount("9345432123")).thenReturn(dto);

        mockMvc.perform(get("/api/fetch")
                .param("mobileNumber", "9345432123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.mobileNumber").value("9345432123"));

        verify(iAccountsService).fetchAccount("9345432123");
    }

    @Test
    @DisplayName("PUT /api/update returns 200 when the service reports the account was updated")
    void updateAccountDetails_success() throws Exception {
        when(iAccountsService.updateAccount(any(CustomerDto.class))).thenReturn(true);

        CustomerDto dto = new CustomerDto();
        dto.setName("Ada Lovelace");
        dto.setEmail("ada@example.com");
        dto.setMobileNumber("9345432123");

        mockMvc.perform(put("/api/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(AccountsConstants.STATUS_200))
                .andExpect(jsonPath("$.statusMsg").value(AccountsConstants.MESSAGE_200));
    }

    @Test
    @DisplayName("PUT /api/update returns 417 when the service reports the account was NOT updated")
    void updateAccountDetails_failure() throws Exception {
        when(iAccountsService.updateAccount(any(CustomerDto.class))).thenReturn(false);

        CustomerDto dto = new CustomerDto();
        dto.setName("Ada Lovelace");
        dto.setEmail("ada@example.com");
        dto.setMobileNumber("9345432123");

        mockMvc.perform(put("/api/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isExpectationFailed())
                .andExpect(jsonPath("$.statusCode").value(AccountsConstants.STATUS_417))
                .andExpect(jsonPath("$.statusMsg").value(AccountsConstants.MESSAGE_417_UPDATE));
    }

    @Test
    @DisplayName("DELETE /api/delete returns 200 and forwards the mobileNumber when deletion succeeds")
    void deleteAccountDetails_success() throws Exception {
        when(iAccountsService.deleteAccount("9345432123")).thenReturn(true);

        mockMvc.perform(delete("/api/delete").param("mobileNumber", "9345432123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(AccountsConstants.STATUS_200))
                .andExpect(jsonPath("$.statusMsg").value(AccountsConstants.MESSAGE_200));

        verify(iAccountsService).deleteAccount("9345432123");
    }

    @Test
    @DisplayName("DELETE /api/delete returns 417 when the service reports the account was NOT deleted")
    void deleteAccountDetails_failure() throws Exception {
        when(iAccountsService.deleteAccount("9345432123")).thenReturn(false);

        mockMvc.perform(delete("/api/delete").param("mobileNumber", "9345432123"))
                .andExpect(status().isExpectationFailed())
                .andExpect(jsonPath("$.statusCode").value(AccountsConstants.STATUS_417))
                .andExpect(jsonPath("$.statusMsg").value(AccountsConstants.MESSAGE_417_DELETE));
    }

    @Test
    @DisplayName("GET /api/build-info returns 200 with the build.version property")
    void getBuildInfo_returnsBuildVersion() throws Exception {
        mockMvc.perform(get("/api/build-info"))
                .andExpect(status().isOk())
                .andExpect(content().string("test-1.2.3"));
    }

    @Test
    @DisplayName("GET /api/java-version returns 200 with the JAVA_HOME value from Environment")
    void getJavaVersion_returnsEnvironmentJavaHome() throws Exception {
        mockMvc.perform(get("/api/java-version"))
                .andExpect(status().isOk())
                .andExpect(content().string("/opt/java/test-21"));
    }

    @Test
    @DisplayName("POST /api/create with an invalid body returns 400 with per-field validation errors and never calls the service")
    void createAccount_invalidBody_returns400() throws Exception {
        CustomerDto invalid = new CustomerDto();

        mockMvc.perform(post("/api/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.email").exists());

        verify(iAccountsService, never()).createAccount(any(CustomerDto.class));
    }

    @Test
    @DisplayName("getBuildInfoFallback returns 200 with hardcoded \"0.9\" regardless of the thrown exception")
    void getBuildInfoFallback_returnsHardcodedVersion() {
        AccountsController controller = new AccountsController(
                mock(IAccountsService.class),
                mock(Environment.class),
                mock(AccountsContactInfoDto.class));

        ResponseEntity<String> response = controller.getBuildInfoFallback(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("0.9");
    }

    @Test
    @DisplayName("getJavaVersionFallback returns 200 with hardcoded \"Java 21\" regardless of the thrown exception")
    void getJavaVersionFallback_returnsHardcodedJavaVersion() {
        AccountsController controller = new AccountsController(
                mock(IAccountsService.class),
                mock(Environment.class),
                mock(AccountsContactInfoDto.class));

        ResponseEntity<String> response = controller.getJavaVersionFallback(new RuntimeException("rate limited"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Java 21");
    }

    @Test
    @DisplayName("GlobalExceptionHandler: ResourceNotFoundException from the service becomes a 404 with an ErrorResponseDto body")
    void fetchAccount_serviceThrowsResourceNotFound_returns404() throws Exception {
        when(iAccountsService.fetchAccount("9999999999"))
                .thenThrow(new ResourceNotFoundException("Customer", "mobileNumber", "9999999999"));

        mockMvc.perform(get("/api/fetch").param("mobileNumber", "9999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.apiPath").value("uri=/api/fetch"))
                .andExpect(jsonPath("$.errorCode").value("404 NOT_FOUND"))
                .andExpect(jsonPath("$.errorMessage").value(
                        "Customer not found with the given input data mobileNumber : '9999999999'"))
                .andExpect(jsonPath("$.errorTime").exists());
    }

    @Test
    @DisplayName("GlobalExceptionHandler: CustomerAlreadyExistsException from the service becomes a 400 with an ErrorResponseDto body")
    void createAccount_serviceThrowsAlreadyExists_returns400() throws Exception {
        CustomerDto dto = new CustomerDto();
        dto.setName("Ada Lovelace");
        dto.setEmail("ada@example.com");
        dto.setMobileNumber("9345432123");

        doThrow(new CustomerAlreadyExistsException(
                "Customer already registered with given mobileNumber 9345432123"))
                .when(iAccountsService).createAccount(any(CustomerDto.class));

        mockMvc.perform(post("/api/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.apiPath").value("uri=/api/create"))
                .andExpect(jsonPath("$.errorCode").value("400 BAD_REQUEST"))
                .andExpect(jsonPath("$.errorMessage").value(
                        "Customer already registered with given mobileNumber 9345432123"))
                .andExpect(jsonPath("$.errorTime").exists());
    }

    @Test
    @DisplayName("GlobalExceptionHandler: any other exception from the service becomes a 500 with an ErrorResponseDto body")
    void fetchAccount_serviceThrowsGenericException_returns500() throws Exception {
        when(iAccountsService.fetchAccount("9345432123"))
                .thenThrow(new RuntimeException("database is on fire"));

        mockMvc.perform(get("/api/fetch").param("mobileNumber", "9345432123"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.apiPath").value("uri=/api/fetch"))
                .andExpect(jsonPath("$.errorCode").value("500 INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.errorMessage").value("database is on fire"))
                .andExpect(jsonPath("$.errorTime").exists());
    }
}
