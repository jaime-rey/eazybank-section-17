package com.eazybytes.insurance.hexagonal.createpolicy.infrastructure.persistence;

import com.eazybytes.insurance.hexagonal.createpolicy.domain.CoverageType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "policies")
public class PolicyEntity {

    @Id
    private String policyNumber;

    private Long customerId;

    @Enumerated(EnumType.STRING)
    private CoverageType coverageType;

    private BigDecimal premium;

    private LocalDate startDate;

    private LocalDate endDate;

    public PolicyEntity() {
    }

    public PolicyEntity(String policyNumber, Long customerId, CoverageType coverageType,
                        BigDecimal premium, LocalDate startDate, LocalDate endDate) {
        this.policyNumber = policyNumber;
        this.customerId = customerId;
        this.coverageType = coverageType;
        this.premium = premium;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public String getPolicyNumber() { return policyNumber; }
    public void setPolicyNumber(String policyNumber) { this.policyNumber = policyNumber; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public CoverageType getCoverageType() { return coverageType; }
    public void setCoverageType(CoverageType coverageType) { this.coverageType = coverageType; }

    public BigDecimal getPremium() { return premium; }
    public void setPremium(BigDecimal premium) { this.premium = premium; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
}
