package com.florin.bank.transaction;

import com.florin.bank.account.Account;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One line of an account's statement. {@code balanceAfter} is the account balance once this line was
 * applied, so a statement shows a running balance without recomputing history.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    public enum Direction { CREDIT, DEBIT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 6)
    private Direction direction;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    LedgerEntry(Transaction transaction, Account account, Direction direction, BigDecimal amount,
                BigDecimal balanceAfter, Instant createdAt) {
        this.transaction = transaction;
        this.account = account;
        this.direction = direction;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Transaction getTransaction() { return transaction; }
    public Account getAccount() { return account; }
    public Direction getDirection() { return direction; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public Instant getCreatedAt() { return createdAt; }
}
