package com.florin.bank.payee;

import com.florin.bank.account.Account;
import com.florin.bank.customer.Customer;
import jakarta.persistence.*;

import java.time.Instant;

/** Someone the customer pays regularly, saved under a nickname of their choosing. */
@Entity
@Table(name = "payees")
public class Payee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, length = 40)
    private String nickname;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Payee() {
    }

    public Payee(Customer customer, Account account, String nickname, Instant createdAt) {
        this.customer = customer;
        this.account = account;
        this.nickname = nickname;
        this.createdAt = createdAt;
    }

    void rename(String nickname) {
        this.nickname = nickname;
    }

    public Long getId() { return id; }
    public Account getAccount() { return account; }
    public String getNickname() { return nickname; }
    public Instant getCreatedAt() { return createdAt; }
}
