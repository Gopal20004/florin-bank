package com.florin.bank.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    @Query("select s.customer.id from AuthSession s where s.tokenHash = :tokenHash and s.expiresAt > :now")
    Optional<Long> findCustomerId(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Query("select s from AuthSession s where s.customer.id = :customerId and s.expiresAt > :now"
            + " order by s.createdAt desc, s.id desc")
    List<AuthSession> findActive(@Param("customerId") Long customerId, @Param("now") Instant now);

    Optional<AuthSession> findByIdAndCustomerId(Long id, Long customerId);

    List<AuthSession> findByCustomerId(Long customerId);

    void deleteByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from AuthSession s where s.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
