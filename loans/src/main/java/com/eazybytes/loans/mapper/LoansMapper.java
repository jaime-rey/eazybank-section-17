package com.eazybytes.loans.mapper;

import com.eazybytes.loans.dto.LoansDto;
import com.eazybytes.loans.entity.Loans;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface LoansMapper {

    LoansDto toDto(Loans loans);

    void updateEntity(LoansDto loansDto, @MappingTarget Loans loans);
}
