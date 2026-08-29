package com.eazybytes.insurance.hexagonal.createpolicy.domain;

public class PolicyValidationException extends RuntimeException {
    public PolicyValidationException(String message) {
        super(message);
    }
}
