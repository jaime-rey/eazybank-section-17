package com.eazybytes.accounts.dto;

import java.math.BigDecimal;

// Spring Data projection: an interface with getters. Spring Data creates a
// runtime proxy whose getters return ResultSet columns matched by alias name.
// Lightweight alternative to returning a full entity when you only need a
// handful of fields — a natural fit for native queries with JOINs.
public interface BeneficiarySummary {
    Long getAccountNumber();
    String getFullName();
    BigDecimal getPercentage();
}
