package com.eazybytes.cards.mapper;

import com.eazybytes.cards.dto.CardsDto;
import com.eazybytes.cards.entity.Cards;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CardsMapperTest {

    @Test
    @DisplayName("mapToCardsDto copies all fields from Cards to CardsDto and returns the same DTO instance")
    void mapToCardsDto_copiesFields() {
        Cards source = new Cards();
        source.setCardNumber("1234567890123456");
        source.setCardType("Credit");
        source.setMobileNumber("9345432123");
        source.setTotalLimit(100000);
        source.setAvailableAmount(30000);
        source.setAmountUsed(70000);
        CardsDto target = new CardsDto();

        CardsDto result = CardsMapper.mapToCardsDto(source, target);

        assertThat(result).isSameAs(target);
        assertThat(result.getCardNumber()).isEqualTo("1234567890123456");
        assertThat(result.getCardType()).isEqualTo("Credit");
        assertThat(result.getMobileNumber()).isEqualTo("9345432123");
        assertThat(result.getTotalLimit()).isEqualTo(100000);
        assertThat(result.getAvailableAmount()).isEqualTo(30000);
        assertThat(result.getAmountUsed()).isEqualTo(70000);
    }

    @Test
    @DisplayName("mapToCards copies all fields from CardsDto to Cards entity and returns the same entity instance")
    void mapToCards_copiesFields() {
        CardsDto source = new CardsDto();
        source.setCardNumber("9876543210987654");
        source.setCardType("Debit");
        source.setMobileNumber("9345432124");
        source.setTotalLimit(50000);
        source.setAvailableAmount(45000);
        source.setAmountUsed(5000);
        Cards target = new Cards();

        Cards result = CardsMapper.mapToCards(source, target);

        assertThat(result).isSameAs(target);
        assertThat(result.getCardNumber()).isEqualTo("9876543210987654");
        assertThat(result.getCardType()).isEqualTo("Debit");
        assertThat(result.getMobileNumber()).isEqualTo("9345432124");
        assertThat(result.getTotalLimit()).isEqualTo(50000);
        assertThat(result.getAvailableAmount()).isEqualTo(45000);
        assertThat(result.getAmountUsed()).isEqualTo(5000);
    }
}
