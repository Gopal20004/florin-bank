package com.florin.bank.account;

import com.florin.bank.common.Money;
import com.florin.bank.customer.Customer;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account {

    public enum Type { SAVINGS, CURRENT }

    /** FROZEN stops money leaving the account but still lets it arrive; CLOSED stops both. */
    public enum Status { ACTIVE, FROZEN, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "account_number", nullable = false, unique = true, length = 12)
    private String accountNumber;

    // Stored as plain text so the column stays a VARCHAR rather than a MySQL ENUM.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private Type type;

    @Column(nullable = false, length = 40)
    private String nickname;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected Account() {
    }

    public Account(Customer customer, String accountNumber, Type type, String nickname, String currency, Instant openedAt) {
        this.customer = customer;
        this.accountNumber = accountNumber;
        this.type = type;
        this.nickname = nickname;
        this.currency = currency;
        this.openedAt = openedAt;
        this.balance = Money.ZERO;
        this.status = Status.ACTIVE;
    }

    public boolean isOwnedBy(Long customerId) {
        return customer.getId().equals(customerId);
    }

    /** Only the ledger calls this, inside a transaction that holds this row's write lock. */
    public void credit(BigDecimal amount) {
        balance = balance.add(amount);
    }

    /** Only the ledger calls this, after checking funds, inside a transaction that holds this row's write lock. */
    public void debit(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new IllegalStateException("Debit of " + amount + " would overdraw account " + id);
        }
        balance = balance.subtract(amount);
    }

    void rename(String nickname) {
        this.nickname = nickname;
    }

    void freeze() {
        status = Status.FROZEN;
    }

    void unfreeze() {
        status = Status.ACTIVE;
    }

    void close(Instant at) {
        status = Status.CLOSED;
        closedAt = at;
    }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public String getAccountNumber() { return accountNumber; }
    public Type getType() { return type; }
    public String getNickname() { return nickname; }
    public BigDecimal getBalance() { return balance; }
    public String getCurrency() { return currency; }
    public Status getStatus() { return status; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getClosedAt() { return closedAt; }
}
