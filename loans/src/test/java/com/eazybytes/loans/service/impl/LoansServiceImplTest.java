package com.eazybytes.loans.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eazybytes.loans.constants.LoansConstants;
import com.eazybytes.loans.dto.LoansDto;
import com.eazybytes.loans.entity.Loans;
import com.eazybytes.loans.exception.LoanAlreadyExistsException;
import com.eazybytes.loans.exception.ResourceNotFoundException;
import com.eazybytes.loans.repository.LoansRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoansServiceImplTest {

    @Mock
    private LoansRepository loansRepository;

    @InjectMocks
    private LoansServiceImpl service;

    // ---------- createLoan ----------

    @Test
    @DisplayName("createLoan: persists a new Loans with defaults (HOME_LOAN type, NEW_LOAN_LIMIT amount, 0 paid) when mobileNumber has no loan yet")
    void createLoan_happyPath() {
        when(loansRepository.findByMobileNumber("9345432123")).thenReturn(Optional.empty());

        service.createLoan("9345432123");

        ArgumentCaptor<Loans> captor = ArgumentCaptor.forClass(Loans.class);
        verify(loansRepository).save(captor.capture());
        Loans saved = captor.getValue();
        assertThat(saved.getMobileNumber()).isEqualTo("9345432123");
        assertThat(saved.getLoanType()).isEqualTo(LoansConstants.HOME_LOAN);
        assertThat(saved.getTotalLoan()).isEqualTo(LoansConstants.NEW_LOAN_LIMIT);
        assertThat(saved.getAmountPaid()).isZero();
        assertThat(saved.getOutstandingAmount()).isEqualTo(LoansConstants.NEW_LOAN_LIMIT);
        assertThat(saved.getLoanNumber()).isNotBlank();
    }

    @Test
    @DisplayName("createLoan: throws LoanAlreadyExistsException when the mobileNumber already has a loan and does not persist anything")
    void createLoan_alreadyExists() {
        when(loansRepository.findByMobileNumber("9345432123")).thenReturn(Optional.of(new Loans()));

        assertThatThrownBy(() -> service.createLoan("9345432123"))
                .isInstanceOf(LoanAlreadyExistsException.class)
                .hasMessageContaining("9345432123");

        verify(loansRepository, never()).save(any());
    }

    // ---------- fetchLoan ----------

    @Test
    @DisplayName("fetchLoan: returns the LoansDto mapped from the entity when the loan exists")
    void fetchLoan_happyPath() {
        Loans loan = new Loans();
        loan.setLoanId(1L);
        loan.setMobileNumber("9345432123");
        loan.setLoanNumber("548732457654");
        loan.setLoanType(LoansConstants.HOME_LOAN);
        loan.setTotalLoan(100_000);
        loan.setAmountPaid(1_000);
        loan.setOutstandingAmount(99_000);
        when(loansRepository.findByMobileNumber("9345432123")).thenReturn(Optional.of(loan));

        LoansDto dto = service.fetchLoan("9345432123");

        assertThat(dto.getMobileNumber()).isEqualTo("9345432123");
        assertThat(dto.getLoanNumber()).isEqualTo("548732457654");
        assertThat(dto.getLoanType()).isEqualTo(LoansConstants.HOME_LOAN);
        assertThat(dto.getTotalLoan()).isEqualTo(100_000);
        assertThat(dto.getAmountPaid()).isEqualTo(1_000);
        assertThat(dto.getOutstandingAmount()).isEqualTo(99_000);
    }

    @Test
    @DisplayName("fetchLoan: throws ResourceNotFoundException when no loan exists for the mobileNumber")
    void fetchLoan_notFound() {
        when(loansRepository.findByMobileNumber("0000000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fetchLoan("0000000000"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Loan")
                .hasMessageContaining("mobileNumber")
                .hasMessageContaining("0000000000");
    }

    // ---------- updateLoan ----------

    @Test
    @DisplayName("updateLoan: applies DTO changes onto the existing loan, persists, and returns true")
    void updateLoan_happyPath() {
        Loans existing = new Loans();
        existing.setLoanId(1L);
        existing.setMobileNumber("9345432123");
        existing.setLoanNumber("548732457654");
        existing.setLoanType(LoansConstants.HOME_LOAN);
        existing.setTotalLoan(100_000);
        existing.setAmountPaid(0);
        existing.setOutstandingAmount(100_000);
        when(loansRepository.findByLoanNumber("548732457654")).thenReturn(Optional.of(existing));

        LoansDto dto = new LoansDto();
        dto.setMobileNumber("9345432123");
        dto.setLoanNumber("548732457654");
        dto.setLoanType(LoansConstants.HOME_LOAN);
        dto.setTotalLoan(200_000);
        dto.setAmountPaid(5_000);
        dto.setOutstandingAmount(195_000);

        boolean updated = service.updateLoan(dto);

        assertThat(updated).isTrue();
        ArgumentCaptor<Loans> captor = ArgumentCaptor.forClass(Loans.class);
        verify(loansRepository).save(captor.capture());
        Loans saved = captor.getValue();
        assertThat(saved.getTotalLoan()).isEqualTo(200_000);
        assertThat(saved.getAmountPaid()).isEqualTo(5_000);
        assertThat(saved.getOutstandingAmount()).isEqualTo(195_000);
    }

    @Test
    @DisplayName("updateLoan: throws ResourceNotFoundException when the loan number does not exist and does not persist anything")
    void updateLoan_notFound() {
        LoansDto dto = new LoansDto();
        dto.setLoanNumber("999999999999");
        when(loansRepository.findByLoanNumber("999999999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateLoan(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Loan")
                .hasMessageContaining("999999999999");

        verify(loansRepository, never()).save(any());
    }

    // ---------- deleteLoan ----------

    @Test
    @DisplayName("deleteLoan: deletes the loan by id and returns true when the mobileNumber matches an existing loan")
    void deleteLoan_happyPath() {
        Loans existing = new Loans();
        existing.setLoanId(1L);
        existing.setMobileNumber("9345432123");
        when(loansRepository.findByMobileNumber("9345432123")).thenReturn(Optional.of(existing));

        boolean deleted = service.deleteLoan("9345432123");

        assertThat(deleted).isTrue();
        verify(loansRepository).deleteById(1L);
    }

    @Test
    @DisplayName("deleteLoan: throws ResourceNotFoundException when no loan exists and does not delete anything")
    void deleteLoan_notFound() {
        when(loansRepository.findByMobileNumber("0000000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteLoan("0000000000"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Loan")
                .hasMessageContaining("0000000000");

        verify(loansRepository, never()).deleteById(any());
    }

}
