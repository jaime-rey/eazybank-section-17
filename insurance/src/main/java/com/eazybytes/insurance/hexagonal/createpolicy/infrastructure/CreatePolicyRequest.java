package com.eazybytes.insurance.hexagonal.createpolicy.infrastructure;

import com.eazybytes.insurance.hexagonal.createpolicy.domain.CoverageType;
import com.eazybytes.insurance.hexagonal.createpolicy.domain.CreatePolicyCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreatePolicyRequest(
    @NotNull Long customerId,
    @NotNull CoverageType coverageType,
    @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal premium,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate
) {
    public CreatePolicyCommand toCommand() {
        return new CreatePolicyCommand(customerId, coverageType, premium, startDate, endDate);
    }
}
