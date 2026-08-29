package com.eazybytes.insurance.hexagonal.createpolicy.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class CreatePolicyUseCaseTest {

    FakePolicyRepository fakeRepository;
    FakePolicyNotification fakeNotification;
    CreatePolicyUseCase useCase;
    LocalDate today;

    @BeforeEach
    void setUp() {
        fakeRepository = new FakePolicyRepository();
        fakeNotification = new FakePolicyNotification();
        useCase = new CreatePolicyUseCase(fakeRepository, fakeNotification);
        today = LocalDate.of(2026, 8, 29);
    }

    @Test
    void creates_policy_when_command_is_valid() {
        CreatePolicyCommand command = validCommand(CoverageType.AUTO);

        Policy createdPolicy = useCase.execute(command, today);

        assertThat(createdPolicy.policyNumber()).isNotBlank();
        assertThat(createdPolicy.customerId()).isEqualTo(command.customerId());
        assertThat(createdPolicy.coverageType()).isEqualTo(CoverageType.AUTO);
        assertThat(createdPolicy.premium()).isEqualByComparingTo(command.premium());
        assertThat(createdPolicy.startDate()).isEqualTo(command.startDate());
        assertThat(createdPolicy.endDate()).isEqualTo(command.endDate());

        assertThat(fakeRepository.saved()).containsExactly(createdPolicy);
        assertThat(fakeNotification.notified()).containsExactly(createdPolicy);
    }

    @Test
    void rejects_policy_when_premium_is_zero_or_negative(){

        CreatePolicyCommand command = new CreatePolicyCommand(
            1L, CoverageType.AUTO, BigDecimal.ZERO, today, today.plusYears(1)
        );

        assertThatThrownBy(() -> useCase.execute(command, today))
            .isInstanceOf(PolicyValidationException.class)
            .hasMessageContaining("premium must be greater than zero");

        assertThat(fakeRepository.saved()).isEmpty();
        assertThat(fakeNotification.notified()).isEmpty();
    }

    @Test
    void rejects_policy_when_start_date_is_in_the_past(){

        CreatePolicyCommand command = new CreatePolicyCommand(
            1L, CoverageType.AUTO, new BigDecimal("100.00"), today.minusDays(1), today.plusYears(1)
        );

        assertThatThrownBy(() -> useCase.execute(command, today))
            .isInstanceOf(PolicyValidationException.class)
            .hasMessageContaining("startDate cannot be in the past");

        assertThat(fakeRepository.saved()).isEmpty();
        assertThat(fakeNotification.notified()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejects_policy_when_end_date_is_not_after_start_date(long daysAfterStart) {
        LocalDate startDate = today;
        LocalDate endDate = today.plusDays(daysAfterStart);
        CreatePolicyCommand command = new CreatePolicyCommand(
            1L, CoverageType.AUTO, new BigDecimal("100.00"), startDate, endDate
        );

        assertThatThrownBy(() -> useCase.execute(command, today))
            .isInstanceOf(PolicyValidationException.class)
            .hasMessageContaining("endDate must be after startDate");

        assertThat(fakeRepository.saved()).isEmpty();
        assertThat(fakeNotification.notified()).isEmpty();
    }

    @Test
    void rejects_second_health_policy_for_same_customer() {
        fakeRepository.simulateExistingHealthPolicyFor(42L);

        CreatePolicyCommand command = new CreatePolicyCommand(
            42L, CoverageType.HEALTH, new BigDecimal("100.00"), today, today.plusYears(1)
        );

        assertThatThrownBy(() -> useCase.execute(command, today))
            .isInstanceOf(PolicyValidationException.class)
            .hasMessageContaining("customer 42 already has an active HEALTH policy");

        assertThat(fakeRepository.saved()).isEmpty();
        assertThat(fakeNotification.notified()).isEmpty();
    }

    @Test
    void allows_multiple_auto_policies_for_same_customer() {
        fakeRepository.simulateExistingHealthPolicyFor(42L);

        CreatePolicyCommand command = new CreatePolicyCommand(
            42L, CoverageType.AUTO, new BigDecimal("100.00"), today, today.plusYears(1)
        );

        Policy created = useCase.execute(command, today);

        assertThat(created).isNotNull();
        assertThat(fakeRepository.saved()).hasSize(1);
    }

    private CreatePolicyCommand validCommand(CoverageType coverageType) {
        return new CreatePolicyCommand(
            1L,
            coverageType,
            new BigDecimal("100.00"),
            today,
            today.plusYears(1)
        );
    }

}
