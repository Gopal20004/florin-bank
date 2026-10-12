package com.florin.bank.auth;

import com.florin.bank.customer.Customer;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "auth_sessions")
public class AuthSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected AuthSession() {
    }

    public AuthSession(Customer customer, String tokenHash, String userAgent, Instant createdAt, Instant expiresAt) {
        this.customer = customer;
        this.tokenHash = tokenHash;
        this.userAgent = userAgent;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public String getUserAgent() { return userAgent; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }

    String getTokenHash() {
        return tokenHash;
    }
}
