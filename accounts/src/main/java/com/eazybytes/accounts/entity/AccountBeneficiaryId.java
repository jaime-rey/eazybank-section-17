package com.eazybytes.accounts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter @Setter @ToString @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class AccountBeneficiaryId implements Serializable {

    @Column(name = "account_number")
    private Long accountNumber;

    @Column(name = "beneficiary_id")
    private Long beneficiaryId;

}

