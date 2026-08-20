package com.eazybytes.loans.mapper;

import com.eazybytes.loans.dto.LoansDto;
import com.eazybytes.loans.entity.Loans;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class LoansMapperTest {

    private final LoansMapper mapper = Mappers.getMapper(LoansMapper.class);

    @Test
    @DisplayName("toDto copies all fields from Loans to a new LoansDto")
    void toDto_copiesFields() {
        Loans source = new Loans();
        source.setLoanNumber("100000001");
        source.setLoanType("Home Loan");
        source.setMobileNumber("9345432123");
        source.setTotalLoan(250000);
        source.setAmountPaid(50000);
        source.setOutstandingAmount(200000);

        LoansDto result = mapper.toDto(source);

        assertThat(result.getLoanNumber()).isEqualTo("100000001");
        assertThat(result.getLoanType()).isEqualTo("Home Loan");
        assertThat(result.getMobileNumber()).isEqualTo("9345432123");
        assertThat(result.getTotalLoan()).isEqualTo(250000);
        assertThat(result.getAmountPaid()).isEqualTo(50000);
        assertThat(result.getOutstandingAmount()).isEqualTo(200000);
    }

    @Test
    @DisplayName("updateEntity copies all fields from LoansDto into the given Loans instance")
    void updateEntity_copiesFields() {
        LoansDto source = new LoansDto();
        source.setLoanNumber("100000002");
        source.setLoanType("Car Loan");
        source.setMobileNumber("9345432124");
        source.setTotalLoan(30000);
        source.setAmountPaid(10000);
        source.setOutstandingAmount(20000);
        Loans target = new Loans();

        mapper.updateEntity(source, target);

        assertThat(target.getLoanNumber()).isEqualTo("100000002");
        assertThat(target.getLoanType()).isEqualTo("Car Loan");
        assertThat(target.getMobileNumber()).isEqualTo("9345432124");
        assertThat(target.getTotalLoan()).isEqualTo(30000);
        assertThat(target.getAmountPaid()).isEqualTo(10000);
        assertThat(target.getOutstandingAmount()).isEqualTo(20000);
    }
}
