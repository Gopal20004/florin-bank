package com.florin.bank.transaction;

import com.florin.bank.account.Account;
import com.florin.bank.customer.Customer;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One money movement as the customer sees it. Its ledger entries record what happened to each account. */
@Entity
@Table(name = "transactions")
public class Transaction {

    public enum Type { DEPOSIT, WITHDRAWAL, TRANSFER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 13)
    private String reference;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 12)
    private Type type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 140)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "initiated_by", nullable = false)
    private Customer initiatedBy;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<LedgerEntry> entries = new ArrayList<>();

    protected Transaction() {
    }

    Transaction(Type type, BigDecimal amount, String currency, String description, Customer initiatedBy,
                String idempotencyKey, String requestHash, Instant createdAt) {
        this.reference = References.next();
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.initiatedBy = initiatedBy;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.createdAt = createdAt;
    }

    /**
     * Moves the money on {@code account} and writes the matching ledger line in one step, so a balance
     * can never change without an entry that explains it.
     */
    void post(Account account, LedgerEntry.Direction direction) {
        if (direction == LedgerEntry.Direction.CREDIT) {
            account.credit(amount);
        } else {
            account.debit(amount);
        }
        entries.add(new LedgerEntry(this, account, direction, amount, account.getBalance(), createdAt));
    }

    /** True when any leg touches one of the customer's accounts: only they may see the transaction. */
    boolean involves(Long customerId) {
        return entries.stream().anyMatch(entry -> entry.getAccount().isOwnedBy(customerId));
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public Type getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getDescription() { return description; }
    public String getRequestHash() { return requestHash; }
    public Instant getCreatedAt() { return createdAt; }
    public List<LedgerEntry> getEntries() { return Collections.unmodifiableList(entries); }
}
