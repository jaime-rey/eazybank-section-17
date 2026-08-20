package com.eazybytes.cards.mapper;

import com.eazybytes.cards.dto.CardsDto;
import com.eazybytes.cards.entity.Cards;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CardsMapper {

    CardsDto toDto(Cards cards);

    void updateEntity(CardsDto cardsDto, @MappingTarget Cards cards);
}
