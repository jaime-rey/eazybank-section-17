package com.eazybytes.cards.mapper;

import com.eazybytes.cards.dto.CardsDto;
import com.eazybytes.cards.entity.Cards;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class CardsMapperTest {

    private final CardsMapper mapper = Mappers.getMapper(CardsMapper.class);

    @Test
    @DisplayName("toDto copies all fields from Cards to a new CardsDto")
    void toDto_copiesFields() {
        Cards source = new Cards();
        source.setCardNumber("1234567890123456");
        source.setCardType("Credit");
        source.setMobileNumber("9345432123");
        source.setTotalLimit(100000);
        source.setAvailableAmount(30000);
        source.setAmountUsed(70000);

        CardsDto result = mapper.toDto(source);

        assertThat(result.getCardNumber()).isEqualTo("1234567890123456");
        assertThat(result.getCardType()).isEqualTo("Credit");
        assertThat(result.getMobileNumber()).isEqualTo("9345432123");
        assertThat(result.getTotalLimit()).isEqualTo(100000);
        assertThat(result.getAvailableAmount()).isEqualTo(30000);
        assertThat(result.getAmountUsed()).isEqualTo(70000);
    }

    @Test
    @DisplayName("updateEntity copies all fields from CardsDto into the given Cards instance")
    void updateEntity_copiesFields() {
        CardsDto source = new CardsDto();
        source.setCardNumber("9876543210987654");
        source.setCardType("Debit");
        source.setMobileNumber("9345432124");
        source.setTotalLimit(50000);
        source.setAvailableAmount(45000);
        source.setAmountUsed(5000);
        Cards target = new Cards();

        mapper.updateEntity(source, target);

        assertThat(target.getCardNumber()).isEqualTo("9876543210987654");
        assertThat(target.getCardType()).isEqualTo("Debit");
        assertThat(target.getMobileNumber()).isEqualTo("9345432124");
        assertThat(target.getTotalLimit()).isEqualTo(50000);
        assertThat(target.getAvailableAmount()).isEqualTo(45000);
        assertThat(target.getAmountUsed()).isEqualTo(5000);
    }
}
