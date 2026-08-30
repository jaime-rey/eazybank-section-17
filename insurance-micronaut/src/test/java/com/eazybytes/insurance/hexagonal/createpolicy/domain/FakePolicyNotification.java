package com.eazybytes.insurance.hexagonal.createpolicy.domain;

import com.eazybytes.insurance.hexagonal.createpolicy.domain.port.PolicyNotificationPort;

import java.util.ArrayList;
import java.util.List;

class FakePolicyNotification implements PolicyNotificationPort {

    private final List<Policy> notifiedPolicies = new ArrayList<>();

    @Override
    public void notifyPolicyCreated(Policy policy) {
        notifiedPolicies.add(policy);
    }

    List<Policy> notified() {
        return notifiedPolicies;
    }
}
