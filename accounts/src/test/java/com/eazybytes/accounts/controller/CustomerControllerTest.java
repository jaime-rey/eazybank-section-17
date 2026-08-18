package com.eazybytes.accounts.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eazybytes.accounts.dto.CustomerDetailsDto;
import com.eazybytes.accounts.service.ICustomersService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ICustomersService iCustomersService;

    @Test
    @DisplayName("GET /api/fetchCustomerDetails returns 200 with the CustomerDetailsDto returned by the service")
    void fetchCustomerDetails_happyPath() throws Exception {
        CustomerDetailsDto dto = new CustomerDetailsDto();
        dto.setName("Ada Lovelace");
        dto.setEmail("ada@example.com");
        dto.setMobileNumber("9345432123");

        when(iCustomersService.fetchCustomerDetails("9345432123", "corr-123")).thenReturn(dto);

        mockMvc.perform(get("/api/fetchCustomerDetails")
                .header("eazybank-correlation-id", "corr-123")
                .param("mobileNumber", "9345432123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.mobileNumber").value("9345432123"));

        verify(iCustomersService).fetchCustomerDetails("9345432123", "corr-123");
    }

    @Test
    @DisplayName("GET /api/fetchCustomerDetails with an invalid mobileNumber returns 400 with per-param validation errors")
    void fetchCustomerDetails_invalidMobileNumber_returns400() throws Exception {
        mockMvc.perform(get("/api/fetchCustomerDetails")
                .header("eazybank-correlation-id", "corr-123")
                .param("mobileNumber", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mobileNumber").value("Mobile number must be 10 digits"));

        verify(iCustomersService, never()).fetchCustomerDetails(anyString(), anyString());
    }

}
