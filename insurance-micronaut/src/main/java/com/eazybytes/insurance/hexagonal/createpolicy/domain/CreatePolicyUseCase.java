package com.eazybytes.insurance.hexagonal.createpolicy.domain;

import com.eazybytes.insurance.hexagonal.createpolicy.domain.port.PolicyNotificationPort;
import com.eazybytes.insurance.hexagonal.createpolicy.domain.port.PolicyRepositoryPort;

import java.time.LocalDate;
import java.util.UUID;

public class CreatePolicyUseCase {

    private final PolicyRepositoryPort policyRepository;
    private final PolicyNotificationPort policyNotification;

    public CreatePolicyUseCase(
        PolicyRepositoryPort policyRepository,
        PolicyNotificationPort policyNotification) {
        this.policyRepository = policyRepository;
        this.policyNotification = policyNotification;
    }

    public Policy execute(CreatePolicyCommand command, LocalDate today) {
        validate(command, today);
        rejectIfDuplicateHealthPolicy(command, today);

        Policy policy = new Policy(
            UUID.randomUUID().toString(),
            command.customerId(),
            command.coverageType(),
            command.premium(),
            command.startDate(),
            command.endDate()
        );

        policyRepository.save(policy);
        policyNotification.notifyPolicyCreated(policy);
        return policy;
    }

    private void validate(CreatePolicyCommand command, LocalDate today) {
        if (command.premium() == null || command.premium().signum() <= 0) {
            throw new PolicyValidationException("premium must be greater than zero");
        }
        if (command.startDate() == null || command.startDate().isBefore(today)) {
            throw new PolicyValidationException("startDate cannot be in the past");
        }
        if (command.endDate() == null || !command.endDate().isAfter(command.startDate())) {
            throw new PolicyValidationException("endDate must be after startDate");
        }
    }

    private void rejectIfDuplicateHealthPolicy(CreatePolicyCommand command, LocalDate today) {
        if (command.coverageType() != CoverageType.HEALTH) {
            return;
        }
        boolean alreadyCovered = policyRepository.existsActivePolicyOfType(
            command.customerId(), CoverageType.HEALTH, today);
        if (alreadyCovered) {
            throw new PolicyValidationException(
                "customer " + command.customerId() + " already has an active HEALTH policy");
        }
    }
}
