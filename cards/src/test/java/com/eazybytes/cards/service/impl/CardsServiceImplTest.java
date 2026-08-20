package com.eazybytes.cards.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eazybytes.cards.constants.CardsConstants;
import com.eazybytes.cards.dto.CardsDto;
import com.eazybytes.cards.entity.Cards;
import com.eazybytes.cards.exception.CardAlreadyExistsException;
import com.eazybytes.cards.exception.ResourceNotFoundException;
import com.eazybytes.cards.mapper.CardsMapper;
import com.eazybytes.cards.repository.CardsRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CardsServiceImplTest {

    @Mock
    private CardsRepository cardsRepository;

    @Spy
    private CardsMapper cardsMapper = Mappers.getMapper(CardsMapper.class);

    @InjectMocks
    private CardsServiceImpl service;

    // ---------- createCard ----------

    @Test
    @DisplayName("createCard: persists a new Cards with defaults (CREDIT_CARD type, NEW_CARD_LIMIT limit, 0 used) when mobileNumber has no card yet")
    void createCard_happyPath() {
        when(cardsRepository.findByMobileNumber("9345432123")).thenReturn(Optional.empty());

        service.createCard("9345432123");

        ArgumentCaptor<Cards> captor = ArgumentCaptor.forClass(Cards.class);
        verify(cardsRepository).save(captor.capture());
        Cards saved = captor.getValue();
        assertThat(saved.getMobileNumber()).isEqualTo("9345432123");
        assertThat(saved.getCardType()).isEqualTo(CardsConstants.CREDIT_CARD);
        assertThat(saved.getTotalLimit()).isEqualTo(CardsConstants.NEW_CARD_LIMIT);
        assertThat(saved.getAmountUsed()).isZero();
        assertThat(saved.getAvailableAmount()).isEqualTo(CardsConstants.NEW_CARD_LIMIT);
        assertThat(saved.getCardNumber()).isNotBlank();
    }

    @Test
    @DisplayName("createCard: throws CardAlreadyExistsException when the mobileNumber already has a card and does not persist anything")
    void createCard_alreadyExists() {
        when(cardsRepository.findByMobileNumber("9345432123")).thenReturn(Optional.of(new Cards()));

        assertThatThrownBy(() -> service.createCard("9345432123"))
                .isInstanceOf(CardAlreadyExistsException.class)
                .hasMessageContaining("9345432123");

        verify(cardsRepository, never()).save(any());
    }

    // ---------- fetchCard ----------

    @Test
    @DisplayName("fetchCard: returns the CardsDto mapped from the entity when the card exists")
    void fetchCard_happyPath() {
        Cards card = new Cards();
        card.setCardId(1L);
        card.setMobileNumber("9345432123");
        card.setCardNumber("100646930341");
        card.setCardType(CardsConstants.CREDIT_CARD);
        card.setTotalLimit(100_000);
        card.setAmountUsed(1_000);
        card.setAvailableAmount(99_000);
        when(cardsRepository.findByMobileNumber("9345432123")).thenReturn(Optional.of(card));

        CardsDto dto = service.fetchCard("9345432123");

        assertThat(dto.getMobileNumber()).isEqualTo("9345432123");
        assertThat(dto.getCardNumber()).isEqualTo("100646930341");
        assertThat(dto.getCardType()).isEqualTo(CardsConstants.CREDIT_CARD);
        assertThat(dto.getTotalLimit()).isEqualTo(100_000);
        assertThat(dto.getAmountUsed()).isEqualTo(1_000);
        assertThat(dto.getAvailableAmount()).isEqualTo(99_000);
    }

    @Test
    @DisplayName("fetchCard: throws ResourceNotFoundException when no card exists for the mobileNumber")
    void fetchCard_notFound() {
        when(cardsRepository.findByMobileNumber("0000000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fetchCard("0000000000"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Card")
                .hasMessageContaining("mobileNumber")
                .hasMessageContaining("0000000000");
    }

    // ---------- updateCard ----------

    @Test
    @DisplayName("updateCard: applies DTO changes onto the existing card, persists, and returns true")
    void updateCard_happyPath() {
        Cards existing = new Cards();
        existing.setCardId(1L);
        existing.setMobileNumber("9345432123");
        existing.setCardNumber("100646930341");
        existing.setCardType(CardsConstants.CREDIT_CARD);
        existing.setTotalLimit(100_000);
        existing.setAmountUsed(0);
        existing.setAvailableAmount(100_000);
        when(cardsRepository.findByCardNumber("100646930341")).thenReturn(Optional.of(existing));

        CardsDto dto = new CardsDto();
        dto.setMobileNumber("9345432123");
        dto.setCardNumber("100646930341");
        dto.setCardType(CardsConstants.CREDIT_CARD);
        dto.setTotalLimit(200_000);
        dto.setAmountUsed(5_000);
        dto.setAvailableAmount(195_000);

        boolean updated = service.updateCard(dto);

        assertThat(updated).isTrue();
        ArgumentCaptor<Cards> captor = ArgumentCaptor.forClass(Cards.class);
        verify(cardsRepository).save(captor.capture());
        Cards saved = captor.getValue();
        assertThat(saved.getTotalLimit()).isEqualTo(200_000);
        assertThat(saved.getAmountUsed()).isEqualTo(5_000);
        assertThat(saved.getAvailableAmount()).isEqualTo(195_000);
    }

    @Test
    @DisplayName("updateCard: throws ResourceNotFoundException when the card number does not exist and does not persist anything")
    void updateCard_notFound() {
        CardsDto dto = new CardsDto();
        dto.setCardNumber("999999999999");
        when(cardsRepository.findByCardNumber("999999999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCard(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Card")
                .hasMessageContaining("999999999999");

        verify(cardsRepository, never()).save(any());
    }

    // ---------- deleteCard ----------

    @Test
    @DisplayName("deleteCard: deletes the card by id and returns true when the mobileNumber matches an existing card")
    void deleteCard_happyPath() {
        Cards existing = new Cards();
        existing.setCardId(1L);
        existing.setMobileNumber("9345432123");
        when(cardsRepository.findByMobileNumber("9345432123")).thenReturn(Optional.of(existing));

        boolean deleted = service.deleteCard("9345432123");

        assertThat(deleted).isTrue();
        verify(cardsRepository).deleteById(1L);
    }

    @Test
    @DisplayName("deleteCard: throws ResourceNotFoundException when no card exists and does not delete anything")
    void deleteCard_notFound() {
        when(cardsRepository.findByMobileNumber("0000000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteCard("0000000000"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Card")
                .hasMessageContaining("0000000000");

        verify(cardsRepository, never()).deleteById(any());
    }

}
