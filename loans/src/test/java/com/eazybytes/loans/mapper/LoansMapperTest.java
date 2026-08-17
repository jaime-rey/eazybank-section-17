package com.eazybytes.loans.mapper;

import com.eazybytes.loans.dto.LoansDto;
import com.eazybytes.loans.entity.Loans;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoansMapperTest {

    @Test
    @DisplayName("mapToLoansDto copies all fields from Loans to LoansDto and returns the same DTO instance")
    void mapToLoansDto_copiesFields() {
        Loans source = new Loans();
        source.setLoanNumber("100000001");
        source.setLoanType("Home Loan");
        source.setMobileNumber("9345432123");
        source.setTotalLoan(250000);
        source.setAmountPaid(50000);
        source.setOutstandingAmount(200000);
        LoansDto target = new LoansDto();

        LoansDto result = LoansMapper.mapToLoansDto(source, target);

        assertThat(result).isSameAs(target);
        assertThat(result.getLoanNumber()).isEqualTo("100000001");
        assertThat(result.getLoanType()).isEqualTo("Home Loan");
        assertThat(result.getMobileNumber()).isEqualTo("9345432123");
        assertThat(result.getTotalLoan()).isEqualTo(250000);
        assertThat(result.getAmountPaid()).isEqualTo(50000);
        assertThat(result.getOutstandingAmount()).isEqualTo(200000);
    }

    @Test
    @DisplayName("mapToLoans copies all fields from LoansDto to Loans entity and returns the same entity instance")
    void mapToLoans_copiesFields() {
        LoansDto source = new LoansDto();
        source.setLoanNumber("100000002");
        source.setLoanType("Car Loan");
        source.setMobileNumber("9345432124");
        source.setTotalLoan(30000);
        source.setAmountPaid(10000);
        source.setOutstandingAmount(20000);
        Loans target = new Loans();

        Loans result = LoansMapper.mapToLoans(source, target);

        assertThat(result).isSameAs(target);
        assertThat(result.getLoanNumber()).isEqualTo("100000002");
        assertThat(result.getLoanType()).isEqualTo("Car Loan");
        assertThat(result.getMobileNumber()).isEqualTo("9345432124");
        assertThat(result.getTotalLoan()).isEqualTo(30000);
        assertThat(result.getAmountPaid()).isEqualTo(10000);
        assertThat(result.getOutstandingAmount()).isEqualTo(20000);
    }
}
