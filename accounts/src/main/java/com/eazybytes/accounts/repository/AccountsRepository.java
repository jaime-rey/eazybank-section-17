package com.eazybytes.accounts.repository;

import com.eazybytes.accounts.entity.Accounts;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Optional;

public interface AccountsRepository extends JpaRepository<Accounts, Long> {

    // Underscore separates navigation from property name:
    // Customer_CustomerId → navigate `customer`, then read `customerId`.

    Optional<Accounts> findByCustomer_CustomerId(Long customerId);

    @Transactional
    @Modifying
    void deleteByCustomer_CustomerId(Long customerId);

}
