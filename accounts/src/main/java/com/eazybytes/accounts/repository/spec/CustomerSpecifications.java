package com.eazybytes.accounts.repository.spec;


import com.eazybytes.accounts.entity.Customer;
import org.springframework.data.jpa.domain.Specification;

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

    public static Specification<Customer> hasAtLeastOneAccount() {
        return (root, query, cb) -> {
            query.distinct(true);
            return cb.isNotEmpty(root.get("accounts"));
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
}
