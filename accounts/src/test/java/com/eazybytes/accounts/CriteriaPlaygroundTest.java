package com.eazybytes.accounts;

import com.eazybytes.accounts.audit.AuditAwareImpl;
import com.eazybytes.accounts.config.JpaAuditingConfig;
import com.eazybytes.accounts.entity.Accounts;
import com.eazybytes.accounts.entity.Customer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({JpaAuditingConfig.class, AuditAwareImpl.class})
class CriteriaPlaygroundTest {

    @Autowired
    private EntityManager em;

    @Test
    void jpql_findAllCustomers() {
        persistAll(customer("Alice", "a@x.com", "5550000001"));

        List<Customer> viaJpql = em.createQuery(
            "SELECT c FROM Customer c", Customer.class).getResultList();

        assertThat(viaJpql).hasSize(1);
    }

    @Test
    void criteria_mobileStartsWith() {
        persistAll(
            customer("Alice",   "a@x.com", "5551110000"),
            customer("Bob",     "b@x.com", "5559990000"),
            customer("Charlie", "c@x.com", "6660000000")
        );

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Customer> cq = cb.createQuery(Customer.class);
        Root<Customer> root = cq.from(Customer.class);

        cq.where(cb.like(root.get("mobileNumber"), "555%"));

        List<Customer> result = em.createQuery(cq).getResultList();

        assertThat(result)
            .hasSize(2)
            .extracting(Customer::getMobileNumber)
            .containsExactlyInAnyOrder("5551110000", "5559990000");
    }

    @Test
    void criteria_orderByNameLengthDesc() {
        persistAll(
            customer("Al",           "a@x.com", "5550000001"),
            customer("Bob",          "b@x.com", "5550000002"),
            customer("Charlie Long", "c@x.com", "5550000003")
        );

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Customer> cq = cb.createQuery(Customer.class);
        Root<Customer> root = cq.from(Customer.class);

        Expression<Integer> nameLength = cb.length(root.get("name"));
        cq.orderBy(cb.desc(nameLength));

        List<Customer> result = em.createQuery(cq)
            .setMaxResults(1)
            .getResultList();

        assertThat(result)
            .hasSize(1)
            .extracting(Customer::getName)
            .containsExactly("Charlie Long");
    }

    @Test
    void criteria_composedAndOr() {
        persistAll(
            customer("Alice",   "a@x.com", "5551110000"),
            customer("Bob",     "b@x.com", "5559990000"),
            customer("Charlie", "c@y.com", "6660000000"),
            customer("Diana", "d@x.com", "7770000000")
        );

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Customer> cq = cb.createQuery(Customer.class);
        Root<Customer> root = cq.from(Customer.class);

        Predicate mobileLike555 = cb.like(root.get("mobileNumber"), "555%");
        Predicate emailLikeX = cb.like(root.get("email"), "%@x.com");
        Predicate nameEqDiana = cb.equal(root.get("name"), "Diana");

        Predicate inner = cb.and(mobileLike555, emailLikeX);
        cq.where(cb.or(inner, nameEqDiana));

        List<Customer> result = em.createQuery(cq).getResultList();

        assertThat(result)
            .extracting(Customer::getName)
            .containsExactlyInAnyOrder("Alice", "Bob", "Diana");
    }

    @Test
    void join_demonstratesNPlusOne() {
        persistAll(
            customerWithAccounts("Alice",   "a@x.com", "5550000001", 2),
            customerWithAccounts("Bob",     "b@x.com", "5550000002", 2),
            customerWithAccounts("Charlie", "c@x.com", "5550000003", 2)
        );
        em.clear();  // wipe the persistence context — force real SELECTs, no cache hits

        Statistics stats = stats();
        stats.clear();

        List<Customer> customers = em.createQuery(
            "SELECT c FROM Customer c", Customer.class).getResultList();

        int totalAccounts = 0;
        for (Customer c : customers) {
            totalAccounts += c.getAccounts().size();   // triggers a lazy SELECT per customer
        }

        assertThat(totalAccounts).isEqualTo(6);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1 + customers.size());
        // 1 SELECT customers + 1 SELECT accounts per customer
    }

    @Test
    void join_fetchAvoidsNPlusOne() {
        persistAll(
            customerWithAccounts("Alice",   "a@x.com", "5550000001", 2),
            customerWithAccounts("Bob",     "b@x.com", "5550000002", 2),
            customerWithAccounts("Charlie", "c@x.com", "5550000003", 2)
        );
        em.clear();

        Statistics stats = stats();
        stats.clear();

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Customer> cq = cb.createQuery(Customer.class);
        Root<Customer> root = cq.from(Customer.class);
        root.fetch("accounts", JoinType.LEFT);
        cq.select(root).distinct(true);

        List<Customer> customers = em.createQuery(cq).getResultList();

        int totalAccounts = 0;
        for (Customer c : customers) {
            totalAccounts += c.getAccounts().size(); // no lazy loading now
        }

        assertThat(customers).hasSize(3);
        assertThat(totalAccounts).isEqualTo(6);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void criteria_subquery_customersWithAtLeastTwoAccounts() {
        persistAll(
            customerWithAccounts("Alice",   "a@x.com", "5550000001", 0),
            customerWithAccounts("Bob",     "b@x.com", "5550000002", 1),
            customerWithAccounts("Charlie", "c@x.com", "5550000003", 3)
        );
        em.clear();

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Customer> cq = cb.createQuery(Customer.class);
        Root<Customer> customer = cq.from(Customer.class);

        // Correlated subquery: count accounts belonging to the outer customer
        Subquery<Long> sub = cq.subquery(Long.class);
        Root<Accounts> account = sub.from(Accounts.class);
        sub.select(cb.count(account))
            .where(cb.equal(account.get("customer"), customer));

        cq.where(cb.greaterThanOrEqualTo(sub, 2L));

        List<Customer> result = em.createQuery(cq).getResultList();

        assertThat(result)
            .hasSize(1)
            .extracting(Customer::getName)
            .containsExactly("Charlie");
    }

    private Customer customer(String name, String email, String mobile) {
        Customer c = new Customer();
        c.setName(name);
        c.setEmail(email);
        c.setMobileNumber(mobile);
        return c;
    }

    private void persistAll(Customer... customers) {
        for (Customer c : customers) {
            em.persist(c);
        }
        em.flush();
    }

    private Statistics stats() {
        return em.getEntityManagerFactory()
            .unwrap(SessionFactory.class)
            .getStatistics();
    }

    private Customer customerWithAccounts(String name, String email, String mobile, int nAccounts) {
        Customer c = customer(name, email, mobile);
        for (int i = 0; i < nAccounts; i++) {
            Accounts a = new Accounts();
            a.setAccountType("Savings");
            a.setBranchAddress("Test branch");
            a.setCommunicationSw(false);
            c.addAccount(a);
        }
        return c;
    }

    @Test
    void em_managedIdentityAndDirtyChecking() {
        Customer c = customer("Alice", "a@x.com", "5550000001");
        em.persist(c);
        em.flush();

        Long id = c.getCustomerId(); // check the real getter in Customer.java
        Customer same = em.find(Customer.class, id);

        // Same JVM instance, not just equals — guaranteed by the persistence context
        assertThat(same).isSameAs(c);

        // No em.update(...) call: dirty checking will detect the change on flush
        same.setName("Alice Cooper");
        em.flush();

        // Clear the context so the next find() actually hits the DB
        em.clear();
        Customer reloaded = em.find(Customer.class, id);
        assertThat(reloaded.getName()).isEqualTo("Alice Cooper");
    }

    @Test
    void em_detachedEntityIsNotTracked() {
        Customer c = customer("Bob", "b@x.com", "5550000002");
        em.persist(c);
        em.flush();
        Long id = c.getCustomerId();

        // Remove c from the persistence context
        em.detach(c);

        // Change on a detached entity: Hibernate does not see it
        c.setName("Bob Dylan");
        em.flush();

        em.clear();
        Customer reloaded = em.find(Customer.class, id);
        // Original name survives — the in-memory change never reached the DB
        assertThat(reloaded.getName()).isEqualTo("Bob");
    }

}
