package com.eazybytes.accounts.dto;


import java.time.LocalDateTime;

public record CustomerSearchCriteria(
    String name,
    String email,
    String mobileNumber,
    LocalDateTime createdAfter,
    LocalDateTime createdBefore,
    String createdBy,
    Long minAccounts,
    Boolean hasAccounts,
    String accountType,
    String branchAddress
) {}
