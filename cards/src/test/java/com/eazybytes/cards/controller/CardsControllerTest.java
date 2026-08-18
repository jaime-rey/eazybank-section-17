package com.eazybytes.cards.controller;

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

import com.eazybytes.cards.constants.CardsConstants;
import com.eazybytes.cards.dto.CardsContactInfoDto;
import com.eazybytes.cards.dto.CardsDto;
import com.eazybytes.cards.service.ICardsService;
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

@WebMvcTest(CardsController.class)
@TestPropertySource(properties = {
        "build.version=test-1.2.3",
        "JAVA_HOME=/opt/java/test-21"
})
class CardsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ICardsService iCardsService;

    @MockitoBean
    private CardsContactInfoDto cardsContactInfoDto;

    private CardsDto validCardsDto() {
        CardsDto dto = new CardsDto();
        dto.setMobileNumber("9345432123");
        dto.setCardNumber("100646930341");
        dto.setCardType(CardsConstants.CREDIT_CARD);
        dto.setTotalLimit(100_000);
        dto.setAmountUsed(1_000);
        dto.setAvailableAmount(99_000);
        return dto;
    }

    @Test
    @DisplayName("GET /api/contact-info returns 200 with the injected CardsContactInfoDto payload")
    void getContactInfo_returnsDto() throws Exception {
        when(cardsContactInfoDto.getMessage()).thenReturn("Welcome to EazyBank cards service");
        when(cardsContactInfoDto.getContactDetails()).thenReturn(Map.of(
                "name", "Ada Lovelace",
                "email", "ada@eazybank.com"));
        when(cardsContactInfoDto.getOnCallSupport()).thenReturn(List.of(
                "+1 555 111 2233",
                "+1 555 444 5566"));

        mockMvc.perform(get("/api/contact-info"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Welcome to EazyBank cards service"))
                .andExpect(jsonPath("$.contactDetails.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.contactDetails.email").value("ada@eazybank.com"))
                .andExpect(jsonPath("$.onCallSupport", hasSize(2)))
                .andExpect(jsonPath("$.onCallSupport[0]").value("+1 555 111 2233"))
                .andExpect(jsonPath("$.onCallSupport[1]").value("+1 555 444 5566"));
    }

    @Test
    @DisplayName("POST /api/create returns 201 and forwards the mobileNumber to the service")
    void createCard_happyPath() throws Exception {
        mockMvc.perform(post("/api/create").param("mobileNumber", "9345432123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusCode").value(CardsConstants.STATUS_201))
                .andExpect(jsonPath("$.statusMsg").value(CardsConstants.MESSAGE_201));

        verify(iCardsService).createCard("9345432123");
    }

    @Test
    @DisplayName("POST /api/create with an invalid mobileNumber returns 400 with per-param validation errors")
    void createCard_invalidMobileNumber_returns400() throws Exception {
        mockMvc.perform(post("/api/create").param("mobileNumber", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mobileNumber").value("Mobile number must be 10 digits"));

        verify(iCardsService, never()).createCard(anyString());
    }

    @Test
    @DisplayName("GET /api/fetch returns 200 with the CardsDto returned by the service")
    void fetchCardDetails_happyPath() throws Exception {
        CardsDto dto = validCardsDto();
        when(iCardsService.fetchCard("9345432123")).thenReturn(dto);

        mockMvc.perform(get("/api/fetch")
                .header("eazybank-correlation-id", "corr-123")
                .param("mobileNumber", "9345432123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mobileNumber").value("9345432123"))
                .andExpect(jsonPath("$.cardNumber").value("100646930341"))
                .andExpect(jsonPath("$.cardType").value(CardsConstants.CREDIT_CARD));

        verify(iCardsService).fetchCard("9345432123");
    }

    @Test
    @DisplayName("GET /api/fetch with an invalid mobileNumber returns 400")
    void fetchCardDetails_invalidMobileNumber_returns400() throws Exception {
        mockMvc.perform(get("/api/fetch")
                .header("eazybank-correlation-id", "corr-123")
                .param("mobileNumber", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mobileNumber").value("Mobile number must be 10 digits"));

        verify(iCardsService, never()).fetchCard(anyString());
    }

    @Test
    @DisplayName("PUT /api/update returns 200 when the service reports the card was updated")
    void updateCardDetails_success() throws Exception {
        when(iCardsService.updateCard(any(CardsDto.class))).thenReturn(true);

        mockMvc.perform(put("/api/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCardsDto())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(CardsConstants.STATUS_200))
                .andExpect(jsonPath("$.statusMsg").value(CardsConstants.MESSAGE_200));
    }

    @Test
    @DisplayName("PUT /api/update returns 417 when the service reports the card was NOT updated")
    void updateCardDetails_failure() throws Exception {
        when(iCardsService.updateCard(any(CardsDto.class))).thenReturn(false);

        mockMvc.perform(put("/api/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCardsDto())))
                .andExpect(status().isExpectationFailed())
                .andExpect(jsonPath("$.statusCode").value(CardsConstants.STATUS_417))
                .andExpect(jsonPath("$.statusMsg").value(CardsConstants.MESSAGE_417_UPDATE));
    }

    @Test
    @DisplayName("PUT /api/update with an invalid body returns 400 and never calls the service")
    void updateCardDetails_invalidBody_returns400() throws Exception {
        CardsDto invalid = new CardsDto();

        mockMvc.perform(put("/api/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mobileNumber").exists())
                .andExpect(jsonPath("$.cardNumber").exists())
                .andExpect(jsonPath("$.cardType").exists());

        verify(iCardsService, never()).updateCard(any(CardsDto.class));
    }

    @Test
    @DisplayName("DELETE /api/delete returns 200 and forwards the mobileNumber when deletion succeeds")
    void deleteCardDetails_success() throws Exception {
        when(iCardsService.deleteCard("9345432123")).thenReturn(true);

        mockMvc.perform(delete("/api/delete").param("mobileNumber", "9345432123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(CardsConstants.STATUS_200))
                .andExpect(jsonPath("$.statusMsg").value(CardsConstants.MESSAGE_200));

        verify(iCardsService).deleteCard("9345432123");
    }

    @Test
    @DisplayName("DELETE /api/delete returns 417 when the service reports the card was NOT deleted")
    void deleteCardDetails_failure() throws Exception {
        when(iCardsService.deleteCard("9345432123")).thenReturn(false);

        mockMvc.perform(delete("/api/delete").param("mobileNumber", "9345432123"))
                .andExpect(status().isExpectationFailed())
                .andExpect(jsonPath("$.statusCode").value(CardsConstants.STATUS_417))
                .andExpect(jsonPath("$.statusMsg").value(CardsConstants.MESSAGE_417_DELETE));
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
