package com.eazybytes.insurance.hexagonal.createpolicy.infrastructure;

import com.eazybytes.insurance.hexagonal.createpolicy.domain.CoverageType;
import com.eazybytes.insurance.hexagonal.createpolicy.domain.Policy;
import com.eazybytes.insurance.hexagonal.createpolicy.domain.port.PolicyRepositoryPort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryPolicyRepositoryAdapter implements PolicyRepositoryPort {

    private final Map<String, Policy> store = new ConcurrentHashMap<>();

    @Override
    public void save(Policy policy) {
        store.put(policy.policyNumber(), policy);
    }

    @Override
    public boolean existsActivePolicyOfType(Long customerId, CoverageType coverageType, LocalDate referenceDate) {
        return store.values().stream().anyMatch(policy ->
            policy.customerId().equals(customerId)
                && policy.coverageType() == coverageType
                && !referenceDate.isBefore(policy.startDate())
                && !referenceDate.isAfter(policy.endDate())
        );
    }
}
