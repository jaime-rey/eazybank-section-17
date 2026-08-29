package com.eazybytes.insurance.hexagonal.createpolicy.infrastructure.persistence;

import com.eazybytes.insurance.hexagonal.createpolicy.domain.CoverageType;
import com.eazybytes.insurance.hexagonal.createpolicy.domain.Policy;
import com.eazybytes.insurance.hexagonal.createpolicy.domain.port.PolicyRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Profile("prod")
@RequiredArgsConstructor
public class JpaPolicyRepositoryAdapter implements PolicyRepositoryPort {

    private final SpringDataPolicyRepository springDataPolicyRepository;

    @Override
    public void save(Policy policy) {
        PolicyEntity entity = toEntity(policy);
        springDataPolicyRepository.save(entity);
    }

    @Override
    public boolean existsActivePolicyOfType(Long customerId, CoverageType coverageType, LocalDate referenceDate) {
        return springDataPolicyRepository
            .existsByCustomerIdAndCoverageTypeAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
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
