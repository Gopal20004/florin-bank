package com.florin.bank.customer;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByEmail(String email);

    Optional<Customer> findByEmail(String email);

    /**
     * SELECT ... FOR UPDATE on the customer row. Taken first by anything that enforces a per-customer
     * rule (daily limit, account and payee caps), so two requests for one customer run one after the other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> lockById(@Param("id") Long id);
}
