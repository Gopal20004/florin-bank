package com.florin.bank.payee;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayeeRepository extends JpaRepository<Payee, Long> {

    @EntityGraph(attributePaths = {"account", "account.customer"})
    List<Payee> findByCustomerIdOrderByNicknameAsc(Long customerId);

    @EntityGraph(attributePaths = {"account", "account.customer"})
    Optional<Payee> findByIdAndCustomerId(Long id, Long customerId);

    Optional<Payee> findByCustomerIdAndAccountId(Long customerId, Long accountId);

    long countByCustomerId(Long customerId);
}
