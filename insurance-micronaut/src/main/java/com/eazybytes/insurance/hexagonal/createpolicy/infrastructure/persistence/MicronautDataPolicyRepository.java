package com.eazybytes.insurance.hexagonal.createpolicy.infrastructure.persistence;

import com.eazybytes.insurance.hexagonal.createpolicy.domain.CoverageType;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;

import java.time.LocalDate;

@Repository
public interface MicronautDataPolicyRepository extends GenericRepository<PolicyEntity, String> {

    PolicyEntity save(PolicyEntity entity);

    boolean existsByCustomerIdAndCoverageTypeAndStartDateLessThanEqualsAndEndDateGreaterThanEquals(
        Long customerId, CoverageType coverageType,
        LocalDate startDateReference, LocalDate endDateReference);
}
