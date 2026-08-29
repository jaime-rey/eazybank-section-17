package com.eazybytes.insurance.hexagonal.createpolicy.infrastructure.messaging;

import com.eazybytes.insurance.hexagonal.createpolicy.domain.Policy;
import com.eazybytes.insurance.hexagonal.createpolicy.domain.port.PolicyNotificationPort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
@RequiredArgsConstructor
public class KafkaPolicyNotificationAdapter implements PolicyNotificationPort {

    private static final Logger logger = LoggerFactory.getLogger(KafkaPolicyNotificationAdapter.class);
    private static final String BINDING_NAME = "policyCreated-out-0";

    private final StreamBridge streamBridge;

    @Override
    public void notifyPolicyCreated(Policy policy) {
        boolean sent = streamBridge.send(BINDING_NAME, policy);
        logger.info("Sent policyCreated event for {} to binding {} (result={})",
            policy.policyNumber(), BINDING_NAME, sent);
    }
}
