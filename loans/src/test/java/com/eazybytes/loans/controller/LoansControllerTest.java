package com.eazybytes.loans.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eazybytes.loans.constants.LoansConstants;
import com.eazybytes.loans.dto.LoansContactInfoDto;
import com.eazybytes.loans.dto.LoansDto;
import com.eazybytes.loans.service.ILoansService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(LoansController.class)
@TestPropertySource(properties = {
        "build.version=test-1.2.3",
        "JAVA_HOME=/opt/java/test-21"
})
class LoansControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ILoansService iLoansService;

    @MockitoBean
    private LoansContactInfoDto loansContactInfoDto;

    private LoansDto validLoansDto() {
        LoansDto dto = new LoansDto();
        dto.setMobileNumber("9345432123");
        dto.setLoanNumber("548732457654");
        dto.setLoanType(LoansConstants.HOME_LOAN);
        dto.setTotalLoan(100_000);
        dto.setAmountPaid(1_000);
        dto.setOutstandingAmount(99_000);
        return dto;
    }

    @Test
    @DisplayName("GET /api/contact-info returns 200 with the injected LoansContactInfoDto payload")
    void getContactInfo_returnsDto() throws Exception {
        when(loansContactInfoDto.getMessage()).thenReturn("Welcome to EazyBank loans service");
        when(loansContactInfoDto.getContactDetails()).thenReturn(Map.of(
                "name", "Ada Lovelace",
                "email", "ada@eazybank.com"));
        when(loansContactInfoDto.getOnCallSupport()).thenReturn(List.of(
                "+1 555 111 2233",
                "+1 555 444 5566"));

        mockMvc.perform(get("/api/contact-info"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Welcome to EazyBank loans service"))
                .andExpect(jsonPath("$.contactDetails.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.contactDetails.email").value("ada@eazybank.com"))
                .andExpect(jsonPath("$.onCallSupport", hasSize(2)))
                .andExpect(jsonPath("$.onCallSupport[0]").value("+1 555 111 2233"))
                .andExpect(jsonPath("$.onCallSupport[1]").value("+1 555 444 5566"));
    }

    @Test
    @DisplayName("POST /api/create returns 201 and forwards the mobileNumber to the service")
    void createLoan_happyPath() throws Exception {
        mockMvc.perform(post("/api/create").param("mobileNumber", "9345432123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusCode").value(LoansConstants.STATUS_201))
                .andExpect(jsonPath("$.statusMsg").value(LoansConstants.MESSAGE_201));

        verify(iLoansService).createLoan("9345432123");
    }

    @Test
    @DisplayName("POST /api/create with an invalid mobileNumber returns 400 with per-param validation errors")
    void createLoan_invalidMobileNumber_returns400() throws Exception {
        mockMvc.perform(post("/api/create").param("mobileNumber", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mobileNumber").value("Mobile number must be 10 digits"));

        verify(iLoansService, never()).createLoan(anyString());
    }

    @Test
    @DisplayName("GET /api/fetch returns 200 with the LoansDto returned by the service")
    void fetchLoanDetails_happyPath() throws Exception {
        LoansDto dto = validLoansDto();
        when(iLoansService.fetchLoan("9345432123")).thenReturn(dto);

        mockMvc.perform(get("/api/fetch")
                .header("eazybank-correlation-id", "corr-123")
                .param("mobileNumber", "9345432123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mobileNumber").value("9345432123"))
                .andExpect(jsonPath("$.loanNumber").value("548732457654"))
                .andExpect(jsonPath("$.loanType").value(LoansConstants.HOME_LOAN));

        verify(iLoansService).fetchLoan("9345432123");
    }

    @Test
    @DisplayName("GET /api/fetch with an invalid mobileNumber returns 400")
    void fetchLoanDetails_invalidMobileNumber_returns400() throws Exception {
        mockMvc.perform(get("/api/fetch")
                .header("eazybank-correlation-id", "corr-123")
                .param("mobileNumber", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mobileNumber").value("Mobile number must be 10 digits"));

        verify(iLoansService, never()).fetchLoan(anyString());
    }

    @Test
    @DisplayName("PUT /api/update returns 200 when the service reports the loan was updated")
    void updateLoanDetails_success() throws Exception {
        when(iLoansService.updateLoan(any(LoansDto.class))).thenReturn(true);

        mockMvc.perform(put("/api/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validLoansDto())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(LoansConstants.STATUS_200))
                .andExpect(jsonPath("$.statusMsg").value(LoansConstants.MESSAGE_200));
    }

    @Test
    @DisplayName("PUT /api/update returns 417 when the service reports the loan was NOT updated")
    void updateLoanDetails_failure() throws Exception {
        when(iLoansService.updateLoan(any(LoansDto.class))).thenReturn(false);

        mockMvc.perform(put("/api/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validLoansDto())))
                .andExpect(status().isExpectationFailed())
                .andExpect(jsonPath("$.statusCode").value(LoansConstants.STATUS_417))
                .andExpect(jsonPath("$.statusMsg").value(LoansConstants.MESSAGE_417_UPDATE));
    }

    @Test
    @DisplayName("PUT /api/update with an invalid body returns 400 and never calls the service")
    void updateLoanDetails_invalidBody_returns400() throws Exception {
        LoansDto invalid = new LoansDto();

        mockMvc.perform(put("/api/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mobileNumber").exists())
                .andExpect(jsonPath("$.loanNumber").exists())
                .andExpect(jsonPath("$.loanType").exists());

        verify(iLoansService, never()).updateLoan(any(LoansDto.class));
    }

    @Test
    @DisplayName("DELETE /api/delete returns 200 and forwards the mobileNumber when deletion succeeds")
    void deleteLoanDetails_success() throws Exception {
        when(iLoansService.deleteLoan("9345432123")).thenReturn(true);

        mockMvc.perform(delete("/api/delete").param("mobileNumber", "9345432123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(LoansConstants.STATUS_200))
                .andExpect(jsonPath("$.statusMsg").value(LoansConstants.MESSAGE_200));

        verify(iLoansService).deleteLoan("9345432123");
    }

    @Test
    @DisplayName("DELETE /api/delete returns 417 when the service reports the loan was NOT deleted")
    void deleteLoanDetails_failure() throws Exception {
        when(iLoansService.deleteLoan("9345432123")).thenReturn(false);

        mockMvc.perform(delete("/api/delete").param("mobileNumber", "9345432123"))
                .andExpect(status().isExpectationFailed())
                .andExpect(jsonPath("$.statusCode").value(LoansConstants.STATUS_417))
                .andExpect(jsonPath("$.statusMsg").value(LoansConstants.MESSAGE_417_DELETE));
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

}
