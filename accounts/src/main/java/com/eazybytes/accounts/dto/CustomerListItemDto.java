package com.eazybytes.accounts.dto;

public record CustomerListItemDto(
    Long customerId,
    String name,
    String email,
    String mobileNumber
    ) {
}
