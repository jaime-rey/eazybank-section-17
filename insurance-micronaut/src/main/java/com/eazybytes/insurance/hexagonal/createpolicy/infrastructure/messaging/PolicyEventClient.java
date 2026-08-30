package com.eazybytes.insurance.hexagonal.createpolicy.infrastructure.messaging;

import com.eazybytes.insurance.hexagonal.createpolicy.domain.Policy;
import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.configuration.kafka.annotation.Topic;
import io.micronaut.context.annotation.Requires;

@Requires(env = "prod")
@KafkaClient
public interface PolicyEventClient {

    @Topic("policy-created")
    void sendPolicyCreated(Policy policy);
}
