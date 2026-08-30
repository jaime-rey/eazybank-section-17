package com.eazybytes.insurance.hexagonal.createpolicy.infrastructure.messaging;

import com.eazybytes.insurance.hexagonal.createpolicy.domain.Policy;
import com.eazybytes.insurance.hexagonal.createpolicy.domain.port.PolicyNotificationPort;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Requires(env = "prod")
@Singleton
public class KafkaPolicyNotificationAdapter implements PolicyNotificationPort {

    private static final Logger logger = LoggerFactory.getLogger(KafkaPolicyNotificationAdapter.class);
    private static final String TOPIC = "policy-created";

    private final PolicyEventClient policyEventClient;

    public KafkaPolicyNotificationAdapter(PolicyEventClient policyEventClient) {
        this.policyEventClient = policyEventClient;
    }

    @Override
    public void notifyPolicyCreated(Policy policy) {
        policyEventClient.sendPolicyCreated(policy);
        logger.info("Sent policyCreated event for {} to topic {}", policy.policyNumber(), TOPIC);
    }
}
