package com.eazybytes.insurance.hexagonal.createpolicy.infrastructure.persistence;

import com.eazybytes.insurance.hexagonal.createpolicy.domain.CoverageType;
import com.eazybytes.insurance.hexagonal.createpolicy.domain.Policy;
import com.eazybytes.insurance.hexagonal.createpolicy.domain.port.PolicyRepositoryPort;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.time.LocalDate;

@Requires(env = "prod")
@Singleton
public class JpaPolicyRepositoryAdapter implements PolicyRepositoryPort {

    private final MicronautDataPolicyRepository micronautDataPolicyRepository;

    public JpaPolicyRepositoryAdapter(MicronautDataPolicyRepository micronautDataPolicyRepository) {
        this.micronautDataPolicyRepository = micronautDataPolicyRepository;
    }

    @Override
    public void save(Policy policy) {
        PolicyEntity entity = toEntity(policy);
        micronautDataPolicyRepository.save(entity);
    }

    @Override
    public boolean existsActivePolicyOfType(Long customerId, CoverageType coverageType, LocalDate referenceDate) {
        return micronautDataPolicyRepository
            .existsByCustomerIdAndCoverageTypeAndStartDateLessThanEqualsAndEndDateGreaterThanEquals(
                customerId, coverageType, referenceDate, referenceDate
            );
    }

    private PolicyEntity toEntity(Policy policy) {
        return new PolicyEntity(
            policy.policyNumber(),
            policy.customerId(),
            policy.coverageType(),
            policy.premium(),
            policy.startDate(),
            policy.endDate()
        );
    }
}
