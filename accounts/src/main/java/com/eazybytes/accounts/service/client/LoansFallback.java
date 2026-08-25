package com.eazybytes.accounts.service.client;

import com.eazybytes.accounts.dto.LoansDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class LoansFallback implements LoansFeignClient{

    private static final Logger log = LoggerFactory.getLogger(LoansFallback.class);

    @Override
    public ResponseEntity<LoansDto> fetchLoanDetails(String correlationId, String mobileNumber) {
        log.warn("Loans fallback triggered, mobileNumber={} correlationId={}", mobileNumber, correlationId);
        return null;
    }
}
