package com.florin.bank.customer;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Customer() {
    }

    public Customer(String fullName, String email, String passwordHash, Instant createdAt) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt() { return createdAt; }

    public void rename(String fullName) {
        this.fullName = fullName;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
