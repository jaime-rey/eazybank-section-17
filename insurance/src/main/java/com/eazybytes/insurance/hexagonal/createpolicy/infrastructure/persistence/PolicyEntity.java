package com.eazybytes.insurance.hexagonal.createpolicy.infrastructure.persistence;

import com.eazybytes.insurance.hexagonal.createpolicy.domain.CoverageType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PolicyEntity {

    @Id
    private String policyNumber;

    private Long customerId;

    @Enumerated(EnumType.STRING)
    private CoverageType coverageType;

    private BigDecimal premium;

    private LocalDate startDate;

    private LocalDate endDate;
}
