package com.eazybytes.insurance.hexagonal.createpolicy.infrastructure;

import com.eazybytes.insurance.hexagonal.createpolicy.domain.CreatePolicyUseCase;
import com.eazybytes.insurance.hexagonal.createpolicy.domain.port.PolicyNotificationPort;
import com.eazybytes.insurance.hexagonal.createpolicy.domain.port.PolicyRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CreatePolicyBeans {

    @Bean
    public CreatePolicyUseCase createPolicyUseCase(
            PolicyRepositoryPort policyRepository,
            PolicyNotificationPort policyNotification) {
        return new CreatePolicyUseCase(policyRepository, policyNotification);
    }
}
