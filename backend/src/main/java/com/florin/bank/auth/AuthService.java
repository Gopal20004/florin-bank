package com.florin.bank.auth;

import com.florin.bank.customer.Customer;
import com.florin.bank.customer.CustomerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    // A bank session is short compared with a social app's: a stolen token is useful for a week at most.
    static final Duration SESSION_LIFETIME = Duration.ofDays(7);

    private final CustomerRepository customers;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final SessionCache sessionCache;
    private final Clock clock;
    // Compared against when the email is unknown, so a miss costs as much time as a wrong password and
    // response times do not reveal which emails have accounts.
    private final String decoyHash;

    public AuthService(CustomerRepository customers, AuthSessionRepository sessions,
                       PasswordEncoder passwordEncoder, SessionCache sessionCache, Clock clock) {
        this.customers = customers;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.sessionCache = sessionCache;
        this.clock = clock;
        this.decoyHash = passwordEncoder.encode("decoy-password-for-timing");
    }

    @Transactional
    public LoginResponse login(LoginRequest request, String userAgent) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Optional<Customer> found = customers.findByEmail(email);
        String hash = found.map(Customer::getPasswordHash).orElse(decoyHash);
        if (!passwordEncoder.matches(request.password(), hash) || found.isEmpty()) {
            throw new InvalidCredentialsException();
        }

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(SESSION_LIFETIME);
        sessions.save(new AuthSession(found.get(), hash(token), truncate(userAgent), now, expiresAt));
        return new LoginResponse(token, expiresAt);
    }

    // Not transactional: a cache hit must not borrow a database connection.
    public Optional<Long> authenticate(String token) {
        String tokenHash = hash(token);
        Optional<Long> cached = sessionCache.find(tokenHash);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<Long> customerId = sessions.findCustomerId(tokenHash, clock.instant());
        customerId.ifPresent(id -> sessionCache.store(tokenHash, id));
        return customerId;
    }

    @Transactional
    public void logout(String token) {
        String tokenHash = hash(token);
        sessions.deleteByTokenHash(tokenHash);
        sessionCache.evict(tokenHash);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> sessions(Long customerId, String currentToken) {
        String currentHash = currentToken == null ? "" : hash(currentToken);
        return sessions.findActive(customerId, clock.instant()).stream()
                .map(session -> new SessionResponse(session.getId(), session.getUserAgent(), session.getCreatedAt(),
                        session.getExpiresAt(), session.getTokenHash().equals(currentHash)))
                .toList();
    }

    /** Signs out one device. Revoking your current session is the same as logging out. */
    @Transactional
    public void revoke(Long customerId, Long sessionId) {
        AuthSession session = sessions.findByIdAndCustomerId(sessionId, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        sessions.delete(session);
        sessionCache.evict(session.getTokenHash());
    }

    /** Changing the password signs out every other device; the one that made the change stays signed in. */
    @Transactional
    public void changePassword(Long customerId, String currentToken, ChangePasswordRequest request) {
        Customer customer = customers.findById(customerId).orElseThrow();
        if (!passwordEncoder.matches(request.currentPassword(), customer.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your current password is not correct");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a password you have not used here before");
        }
        customer.changePassword(passwordEncoder.encode(request.newPassword()));
        String keep = currentToken == null ? "" : hash(currentToken);
        for (AuthSession session : sessions.findByCustomerId(customerId)) {
            if (!session.getTokenHash().equals(keep)) {
                sessions.delete(session);
                sessionCache.evict(session.getTokenHash());
            }
        }
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String truncate(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent;
    }
}
