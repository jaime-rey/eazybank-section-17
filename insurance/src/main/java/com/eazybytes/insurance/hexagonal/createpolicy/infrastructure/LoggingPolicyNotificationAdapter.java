package com.eazybytes.insurance.hexagonal.createpolicy.infrastructure;

import com.eazybytes.insurance.hexagonal.createpolicy.domain.Policy;
import com.eazybytes.insurance.hexagonal.createpolicy.domain.port.PolicyNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("dev")
@Component
public class LoggingPolicyNotificationAdapter implements PolicyNotificationPort {

    private static final Logger logger = LoggerFactory.getLogger(LoggingPolicyNotificationAdapter.class);

    @Override
    public void notifyPolicyCreated(Policy policy) {
        logger.info("Policy created: number={} customerId={} coverage={} premium={} start={} end={}",
            policy.policyNumber(),
            policy.customerId(),
            policy.coverageType(),
            policy.premium(),
            policy.startDate(),
            policy.endDate());
    }
}
