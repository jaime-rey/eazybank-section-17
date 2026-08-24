package com.eazybytes.accounts.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class Customer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id")
    private Long customerId;

    private String name;

    private String email;

    @Column(name = "mobile_number")
    private String mobileNumber;

    @ToString.Exclude
    @OneToMany(mappedBy = "customer", cascade = CascadeType.PERSIST)
    private List<Accounts> accounts = new ArrayList<>();

    public void addAccount(Accounts a) {
        accounts.add(a);
        a.setCustomer(this);
    }
}
