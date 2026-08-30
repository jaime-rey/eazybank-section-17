package com.eazybytes.insurance.hexagonal.createpolicy.infrastructure;

import com.eazybytes.insurance.hexagonal.createpolicy.domain.CreatePolicyUseCase;
import com.eazybytes.insurance.hexagonal.createpolicy.domain.port.PolicyNotificationPort;
import com.eazybytes.insurance.hexagonal.createpolicy.domain.port.PolicyRepositoryPort;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
public class CreatePolicyFactory {

    @Singleton
    CreatePolicyUseCase createPolicyUseCase(
        PolicyRepositoryPort policyRepository,
        PolicyNotificationPort policyNotification) {
        return new CreatePolicyUseCase(policyRepository, policyNotification);
    }
}
