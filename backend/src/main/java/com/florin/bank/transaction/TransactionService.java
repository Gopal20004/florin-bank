package com.florin.bank.transaction;

import com.florin.bank.account.AccountNumbers;
import com.florin.bank.common.Money;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Entry point for money movement. Deliberately not transactional: it wraps each {@link Ledger} call
 * (one database transaction) with idempotent replay and a retry after a deadlock.
 */
@Service
public class TransactionService {

    private static final int MAX_ATTEMPTS = 3;
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_:.-]{8,64}");

    private final Ledger ledger;
    private final Clock clock;

    public TransactionService(Ledger ledger, Clock clock) {
        this.ledger = ledger;
        this.clock = clock;
    }

    public Posted deposit(Long customerId, Long accountId, MoneyRequest request, String idempotencyKey) {
        String key = checkKey(idempotencyKey);
        BigDecimal amount = Money.normalize(request.amount());
        String description = cleanDescription(request.description());
        String hash = fingerprint("DEPOSIT", String.valueOf(accountId), amount.toPlainString(), description);
        return post(customerId, key, hash,
                () -> ledger.deposit(customerId, accountId, amount, description, key, hash, clock.instant()));
    }

    public Posted withdraw(Long customerId, Long accountId, MoneyRequest request, String idempotencyKey) {
        String key = checkKey(idempotencyKey);
        BigDecimal amount = Money.normalize(request.amount());
        String description = cleanDescription(request.description());
        String hash = fingerprint("WITHDRAWAL", String.valueOf(accountId), amount.toPlainString(), description);
        return post(customerId, key, hash,
                () -> ledger.withdraw(customerId, accountId, amount, description, key, hash, clock.instant()));
    }

    public Posted transfer(Long customerId, TransferRequest request, String idempotencyKey) {
        String key = checkKey(idempotencyKey);
        String toAccountNumber = AccountNumbers.clean(request.toAccountNumber());
        if (!AccountNumbers.isValid(toAccountNumber)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "That account number doesn't look right. Check all 12 digits and try again.");
        }
        BigDecimal amount = Money.normalize(request.amount());
        String description = cleanDescription(request.description());
        String hash = fingerprint("TRANSFER", String.valueOf(request.fromAccountId()), toAccountNumber,
                amount.toPlainString(), description);
        return post(customerId, key, hash, () -> ledger.transfer(customerId, request.fromAccountId(), toAccountNumber,
                amount, description, key, hash, clock.instant()));
    }

    public TransactionResponse receipt(Long customerId, String reference) {
        return ledger.receipt(customerId, reference);
    }

    public LimitsResponse limits(Long customerId) {
        return ledger.limits(customerId, clock.instant());
    }

    private Posted post(Long customerId, String key, String hash, Supplier<TransactionResponse> action) {
        if (key != null) {
            Optional<Posted> replay = replay(customerId, key, hash);
            if (replay.isPresent()) {
                return replay.get();
            }
        }
        for (int attempt = 1; ; attempt++) {
            try {
                return new Posted(action.get(), false);
            } catch (DataIntegrityViolationException exception) {
                // Two requests with one key raced past the check above. The unique index let the first commit
                // and rolled this one back, money movement included; answer with what the first one did.
                if (key != null) {
                    Optional<Posted> replay = replay(customerId, key, hash);
                    if (replay.isPresent()) {
                        return replay.get();
                    }
                }
                throw exception;
            } catch (PessimisticLockingFailureException exception) {
                // A deadlock or lock timeout rolled the whole transaction back, so running it again is safe.
                if (attempt >= MAX_ATTEMPTS) {
                    throw exception;
                }
            }
        }
    }

    private Optional<Posted> replay(Long customerId, String key, String hash) {
        return ledger.findByIdempotencyKey(customerId, key).map(stored -> {
            if (!hash.equals(stored.requestHash())) {
                throw new IdempotencyKeyReusedException();
            }
            return new Posted(stored.response(), true);
        });
    }

    private static String checkKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        if (!KEY.matcher(idempotencyKey).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must be 8 to 64 letters, digits, '-', '_', ':' or '.'");
        }
        return idempotencyKey;
    }

    /** Collapses whitespace and drops control characters; an empty note is stored as no note. */
    static String cleanDescription(String description) {
        if (description == null) {
            return null;
        }
        String cleaned = description.replaceAll("\\p{Cntrl}", " ").trim().replaceAll("\\s+", " ");
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** SHA-256 over the fields that define a request, so a reused key with different details is caught. */
    static String fingerprint(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                digest.update((part == null ? "" : part).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0x1F);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
