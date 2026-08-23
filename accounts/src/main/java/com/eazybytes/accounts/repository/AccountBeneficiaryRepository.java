package com.eazybytes.accounts.repository;

import com.eazybytes.accounts.dto.BeneficiarySummary;
import com.eazybytes.accounts.entity.AccountBeneficiary;
import com.eazybytes.accounts.entity.AccountBeneficiaryId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountBeneficiaryRepository
    extends JpaRepository<AccountBeneficiary, AccountBeneficiaryId> {

    // Naive: no JOIN. Loading beneficiary lazily inside a loop triggers the N+1.
    List<AccountBeneficiary> findByIdAccountNumber(Long accountNumber);

    // Fix A: declarative @EntityGraph. Spring Data issues a JOIN FETCH under the
    // hood and returns entities with beneficiary already hydrated (not a proxy).
    // 1 SELECT total.
    @EntityGraph(attributePaths = {"beneficiary"})
    List<AccountBeneficiary> findByIdAccountNumberIn(List<Long> accountNumbers);

    // Fix B: native SQL returning a Spring Data projection. We do not load the
    // AccountBeneficiary entity — we return a lightweight DTO (BeneficiarySummary)
    // with only the fields we need. 1 SELECT total, and even more efficient than
    // the full entity: fewer columns and no identity-map or proxy ceremony.
    @Query(value = """
        SELECT ab.account_number AS accountNumber,
               b.full_name       AS fullName,
               ab.percentage     AS percentage
        FROM account_beneficiary ab
        INNER JOIN beneficiary b ON b.id = ab.beneficiary_id
        WHERE ab.account_number IN (:accountNumbers)
        """, nativeQuery = true)
    List<BeneficiarySummary> summariesForAccountsNative(
        @Param("accountNumbers") List<Long> accountNumbers);
}
