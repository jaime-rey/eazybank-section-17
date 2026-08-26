package com.eazybytes.accounts.dto;

public record CustomerSearchDto(
    String name,
    String email,
    String mobileNumberPrefix,
    Boolean hasAccount
    ) {
}
