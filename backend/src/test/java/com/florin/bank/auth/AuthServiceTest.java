package com.florin.bank.auth;

import com.florin.bank.customer.Customer;
import com.florin.bank.customer.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock CustomerRepository customers;
    @Mock AuthSessionRepository sessions;
    @Mock PasswordEncoder passwordEncoder;
    @Mock SessionCache sessionCache;
    final Clock clock = Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneOffset.UTC);
    AuthService authService;

    @BeforeEach
    void setUp() {
        // The service hashes a decoy password once at start-up, for timing-safe misses.
        when(passwordEncoder.encode("decoy-password-for-timing")).thenReturn("decoy-hash");
        authService = new AuthService(customers, sessions, passwordEncoder, sessionCache, clock);
    }

    @Test
    void loginNormalizesEmailAndStoresOnlyTheTokenHash() {
        Customer customer = new Customer("Asha Rao", "asha@example.com", "stored-hash", clock.instant());
        when(customers.findByEmail("asha@example.com")).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("good-password", "stored-hash")).thenReturn(true);

        LoginResponse response = authService.login(new LoginRequest("  ASHA@Example.com ", "good-password"), "JUnit");

        ArgumentCaptor<AuthSession> session = ArgumentCaptor.forClass(AuthSession.class);
        verify(sessions).save(session.capture());
        assertEquals(43, response.token().length());
        assertEquals(AuthService.hash(response.token()), session.getValue().getTokenHash());
        assertNotEquals(response.token(), session.getValue().getTokenHash());
        assertEquals(clock.instant().plus(AuthService.SESSION_LIFETIME), response.expiresAt());
        assertEquals("JUnit", session.getValue().getUserAgent());
    }

    @Test
    void wrongPasswordNeverCreatesASession() {
        Customer customer = new Customer("Asha Rao", "asha@example.com", "stored-hash", clock.instant());
        when(customers.findByEmail("asha@example.com")).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("asha@example.com", "wrong-password"), null));
        verify(sessions, never()).save(any());
    }

    @Test
    void unknownEmailStillPaysForAPasswordCheck() {
        when(customers.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("nobody@example.com", "any-password"), null));
        // Same BCrypt work as a wrong password, so response times don't reveal which emails are customers.
        verify(passwordEncoder).matches("any-password", "decoy-hash");
        verify(sessions, never()).save(any());
    }

    @Test
    void cachedTokensSkipTheDatabase() {
        when(sessionCache.find(AuthService.hash("token-1"))).thenReturn(Optional.of(7L));

        assertEquals(Optional.of(7L), authService.authenticate("token-1"));
        verifyNoInteractions(sessions);
    }

    @Test
    void databaseHitsAreCachedForTheNextRequest() {
        String hash = AuthService.hash("token-2");
        when(sessionCache.find(hash)).thenReturn(Optional.empty());
        when(sessions.findCustomerId(hash, clock.instant())).thenReturn(Optional.of(9L));

        assertEquals(Optional.of(9L), authService.authenticate("token-2"));
        verify(sessionCache).store(hash, 9L);
    }
}
