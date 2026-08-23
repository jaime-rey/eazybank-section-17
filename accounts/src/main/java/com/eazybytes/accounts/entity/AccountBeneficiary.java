package com.eazybytes.accounts.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "account_beneficiary")
@Getter @Setter @ToString @NoArgsConstructor @AllArgsConstructor
public class AccountBeneficiary extends BaseEntity {

    @EmbeddedId
    private AccountBeneficiaryId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("accountNumber")
    @JoinColumn(name = "account_number")
    private Accounts account;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("beneficiaryId")
    @JoinColumn(name = "beneficiary_id")
    private Beneficiary beneficiary;

    @Column(name = "percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;
}
