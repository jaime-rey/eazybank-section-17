package com.eazybytes.accounts.repository.spec;


import com.eazybytes.accounts.dto.CustomerSearchCriteria;
import com.eazybytes.accounts.entity.Accounts;
import com.eazybytes.accounts.entity.Customer;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public final class CustomerSpecifications {

    private CustomerSpecifications() { }

    public static Specification<Customer> nameContains(String value) {
        return (root, query, cb) ->
            cb.like(cb.lower(root.get("name")), "%" + value.toLowerCase() + "%");
    }

    public static Specification<Customer> emailContains(String value) {
        return (root, query, cb) ->
            cb.like(cb.lower(root.get("email")), "%" + value.toLowerCase() + "%");
    }

    public static Specification<Customer> mobileStartsWith(String prefix) {
        return (root, query, cb) ->
            cb.like(root.get("mobileNumber"), prefix + "%");
    }

    public static Specification<Customer> createdAfter(LocalDateTime from) {
        return (root, query, cb) ->
            cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Customer> createdBefore(LocalDateTime to) {
        return (root, query, cb) ->
            cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    public static Specification<Customer> createdByEquals(String user) {
        return (root, query, cb) ->
            cb.equal(root.get("createdBy"), user);
    }

    public static Specification<Customer> hasAtLeastOneAccount() {
        return (root, query, cb) -> {
            query.distinct(true);
            return cb.isNotEmpty(root.get("accounts"));
        };
    }

    public static Specification<Customer> hasAccounts(boolean yes) {
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<Accounts> a = sub.from(Accounts.class);
            sub.select(cb.count(a)).where(cb.equal(a.get("customer"), root));
            return yes ? cb.greaterThan(sub, 0L) : cb.equal(sub, 0L);
        };
    }

    public static Specification<Customer> hasAtLeastNAccounts(long n) {
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<Accounts> a = sub.from(Accounts.class);
            sub.select(cb.count(a)).where(cb.equal(a.get("customer"), root));
            return cb.greaterThanOrEqualTo(sub, n);
        };
    }

    public static Specification<Customer> accountTypeEquals(String type) {
        return (root, query, cb) -> {
            Join<Customer, Accounts> a = root.join("accounts", JoinType.INNER);
            query.distinct(true);
            return cb.equal(a.get("accountType"), type);
        };
    }

    public static Specification<Customer> branchAddressContains(String value) {
        return (root, query, cb) -> {
            Join<Customer, Accounts> a = root.join("accounts", JoinType.INNER);
            query.distinct(true);
            return cb.like(cb.lower(a.get("branchAddress")), "%" + value.toLowerCase() + "%");
        };
    }

    public static Specification<Customer> fetchAccounts() {
        return (root, query, cb) -> {
            Class<?> resultType = query.getResultType();
            if (resultType != Long.class && resultType != long.class) {
                root.fetch("accounts", JoinType.LEFT);
                query.distinct(true);
            }
            return cb.conjunction();
        };
    }

    public static Specification<Customer> build(
        String name,
        String email,
        String mobilePrefix,
        Boolean hasAccount) {

        Specification<Customer> spec = (root, query, cb) -> cb.conjunction();

        if (name != null && !name.isBlank()) {
            spec = spec.and(nameContains(name));
        }
        if (email != null && !email.isBlank()) {
            spec = spec.and(emailContains(email));
        }
        if (mobilePrefix != null && !mobilePrefix.isBlank()) {
            spec = spec.and(mobileStartsWith(mobilePrefix));
        }
        if (Boolean.TRUE.equals(hasAccount)) {
            spec = spec.and(hasAtLeastOneAccount());
        }

        return spec;
    }

    public static Specification<Customer> build(CustomerSearchCriteria c) {
        Specification<Customer> spec = (root, query, cb) -> cb.conjunction();

        if (hasText(c.name()))          spec = spec.and(nameContains(c.name()));
        if (hasText(c.email()))         spec = spec.and(emailContains(c.email()));
        if (hasText(c.mobileNumber()))  spec = spec.and(mobileStartsWith(c.mobileNumber()));
        if (c.createdAfter()  != null)  spec = spec.and(createdAfter(c.createdAfter()));
        if (c.createdBefore() != null)  spec = spec.and(createdBefore(c.createdBefore()));
        if (hasText(c.createdBy()))     spec = spec.and(createdByEquals(c.createdBy()));
        if (c.minAccounts()   != null)  spec = spec.and(hasAtLeastNAccounts(c.minAccounts()));
        if (c.hasAccounts()   != null)  spec = spec.and(hasAccounts(c.hasAccounts()));
        if (hasText(c.accountType()))   spec = spec.and(accountTypeEquals(c.accountType()));
        if (hasText(c.branchAddress())) spec = spec.and(branchAddressContains(c.branchAddress()));

        return spec.and(fetchAccounts());
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
