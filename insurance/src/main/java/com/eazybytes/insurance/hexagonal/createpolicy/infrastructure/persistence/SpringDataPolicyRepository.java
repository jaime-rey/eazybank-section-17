package com.eazybytes.insurance.hexagonal.createpolicy.infrastructure.persistence;

import com.eazybytes.insurance.hexagonal.createpolicy.domain.CoverageType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface SpringDataPolicyRepository extends JpaRepository<PolicyEntity, String> {
    boolean existsByCustomerIdAndCoverageTypeAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
        Long customerId,
        CoverageType coverageType,
        LocalDate startDateReference,
        LocalDate endDateReference
    );
}
