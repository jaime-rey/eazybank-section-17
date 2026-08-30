package com.eazybytes.insurance.hexagonal.createpolicy.domain.port;

import com.eazybytes.insurance.hexagonal.createpolicy.domain.Policy;

public interface PolicyNotificationPort {

    void notifyPolicyCreated(Policy policy);
}
