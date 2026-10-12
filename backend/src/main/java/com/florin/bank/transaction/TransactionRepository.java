package com.florin.bank.transaction;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @EntityGraph(attributePaths = {"entries", "entries.account", "entries.account.customer"})
    Optional<Transaction> findByInitiatedByIdAndIdempotencyKey(Long customerId, String idempotencyKey);

    @EntityGraph(attributePaths = {"entries", "entries.account", "entries.account.customer"})
    Optional<Transaction> findByReference(String reference);
}
