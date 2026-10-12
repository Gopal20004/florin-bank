package com.florin.bank.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByCustomerIdOrderByIdAsc(Long customerId);

    Optional<Account> findByIdAndCustomerId(Long id, Long customerId);

    boolean existsByAccountNumber(String accountNumber);

    long countByCustomerIdAndStatusNot(Long customerId, Account.Status status);

    @EntityGraph(attributePaths = "customer")
    Optional<Account> findWithCustomerByAccountNumber(String accountNumber);

    /** A plain read of the id only, so the entity is first loaded by one of the locking queries below. */
    @Query("select a.id from Account a where a.accountNumber = :accountNumber")
    Optional<Long> findIdByAccountNumber(@Param("accountNumber") String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> lockById(@Param("id") Long id);

    /**
     * Locks every row in ascending id order. All money movement takes account locks this way, so two
     * transfers between the same pair of accounts (in either direction) can never deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id in :ids order by a.id")
    List<Account> lockAllById(@Param("ids") Collection<Long> ids);

    @Query("select sum(a.balance) from Account a where a.customer.id = :customerId")
    BigDecimal totalBalance(@Param("customerId") Long customerId);
}
