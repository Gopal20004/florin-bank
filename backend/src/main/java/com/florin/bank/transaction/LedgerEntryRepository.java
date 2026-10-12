package com.florin.bank.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    /**
     * Statement lines of one customer, newest first. Every filter is optional (null switches it off).
     * {@code pattern} is a lower-case LIKE pattern escaped with '!'; {@code reference} matches exactly.
     */
    @Query(value = """
            select e from LedgerEntry e join fetch e.transaction t join fetch e.account a
            where a.customer.id = :customerId
              and (:accountId is null or a.id = :accountId)
              and (:from is null or e.createdAt >= :from)
              and (:to is null or e.createdAt < :to)
              and (:direction is null or e.direction = :direction)
              and (:pattern is null or lower(t.description) like :pattern escape '!' or t.reference = :reference)
            order by e.createdAt desc, e.id desc
            """,
            countQuery = """
            select count(e) from LedgerEntry e join e.transaction t join e.account a
            where a.customer.id = :customerId
              and (:accountId is null or a.id = :accountId)
              and (:from is null or e.createdAt >= :from)
              and (:to is null or e.createdAt < :to)
              and (:direction is null or e.direction = :direction)
              and (:pattern is null or lower(t.description) like :pattern escape '!' or t.reference = :reference)
            """)
    Page<LedgerEntry> search(@Param("customerId") Long customerId,
                             @Param("accountId") Long accountId,
                             @Param("from") Instant from,
                             @Param("to") Instant to,
                             @Param("direction") LedgerEntry.Direction direction,
                             @Param("pattern") String pattern,
                             @Param("reference") String reference,
                             Pageable pageable);

    /** Both legs of each transfer on a statement page, in one query, to name the other side of each line. */
    @Query("select e from LedgerEntry e join fetch e.account a join fetch a.customer where e.transaction.id in :transactionIds")
    List<LedgerEntry> findLegs(@Param("transactionIds") Collection<Long> transactionIds);

    /**
     * Money that left the customer between two instants: withdrawals, and transfers whose credit leg is not
     * one of the customer's own accounts. Null when there was none.
     */
    @Query("""
            select sum(e.amount) from LedgerEntry e
            where e.account.customer.id = :customerId
              and e.direction = com.florin.bank.transaction.LedgerEntry.Direction.DEBIT
              and e.createdAt >= :from and e.createdAt < :to
              and not exists (select c.id from LedgerEntry c
                              where c.transaction = e.transaction
                                and c.direction = com.florin.bank.transaction.LedgerEntry.Direction.CREDIT
                                and c.account.customer.id = :customerId)
            """)
    BigDecimal outgoingBetween(@Param("customerId") Long customerId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select new com.florin.bank.transaction.LedgerMovement(e.transaction.id, e.direction, e.amount, e.createdAt)
            from LedgerEntry e
            where e.account.customer.id = :customerId and e.createdAt >= :from
            """)
    List<LedgerMovement> movementsSince(@Param("customerId") Long customerId, @Param("from") Instant from);
}
