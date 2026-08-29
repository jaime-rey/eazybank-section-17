package com.eazybytes.insurance.hexagonal.createpolicy.domain;

import com.eazybytes.insurance.hexagonal.createpolicy.domain.port.PolicyRepositoryPort;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class FakePolicyRepository implements PolicyRepositoryPort {

    private final List<Policy> savedPolicies = new ArrayList<>();
    private final Set<Long> customersWithActiveHealthPolicy = new HashSet<>();

    @Override
    public void save(Policy policy) {
        savedPolicies.add(policy);
    }

    @Override
    public boolean existsActivePolicyOfType(Long customerId, CoverageType coverageType, LocalDate referenceDate) {
        return coverageType == CoverageType.HEALTH
            && customersWithActiveHealthPolicy.contains(customerId);
    }

    List<Policy> saved() {
        return savedPolicies;
    }

    void simulateExistingHealthPolicyFor(Long customerId) {
        customersWithActiveHealthPolicy.add(customerId);
    }
}
