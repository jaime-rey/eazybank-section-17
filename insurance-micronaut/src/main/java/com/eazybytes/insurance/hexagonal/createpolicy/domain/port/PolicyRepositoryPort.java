package com.eazybytes.insurance.hexagonal.createpolicy.domain.port;

import com.eazybytes.insurance.hexagonal.createpolicy.domain.CoverageType;
import com.eazybytes.insurance.hexagonal.createpolicy.domain.Policy;

import java.time.LocalDate;

public interface PolicyRepositoryPort {

    void save(Policy policy);

    boolean existsActivePolicyOfType(Long customerId, CoverageType coverageType, LocalDate referenceDate);
}
