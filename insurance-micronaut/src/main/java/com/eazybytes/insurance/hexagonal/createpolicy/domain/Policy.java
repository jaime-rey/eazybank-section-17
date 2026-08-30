package com.eazybytes.insurance.hexagonal.createpolicy.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Policy(
    String policyNumber,
    Long customerId,
    CoverageType coverageType,
    BigDecimal premium,
    LocalDate startDate,
    LocalDate endDate
) {
}
