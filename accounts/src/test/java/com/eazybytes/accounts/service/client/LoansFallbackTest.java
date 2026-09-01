package com.eazybytes.accounts.service.client;

import com.eazybytes.accounts.dto.LoansDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class LoansFallbackTest {

    private final LoansFallback fallback = new LoansFallback();

    @Test
    void fetchLoanDetails_returnsNullAsFallbackContract() {
        ResponseEntity<LoansDto> result = fallback.fetchLoanDetails("corr-1", "9345432123");
        assertThat(result).isNull();
    }
}
