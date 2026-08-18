package com.eazybytes.accounts.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.eazybytes.accounts.constants.AccountsConstants;
import com.eazybytes.accounts.dto.AccountsContactInfoDto;
import com.eazybytes.accounts.dto.CustomerDto;
import com.eazybytes.accounts.service.IAccountsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@WebMvcTest(AccountsController.class)
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
                        .param("mobileNumber","abc"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.mobileNumber").exists())
                        .andExpect(jsonPath("$.mobileNumber").value("Mobile number must be 10 digits"));

                verify(iAccountsService, never()).fetchAccount(anyString());
        }

}