package com.florin.bank.transaction;

import com.florin.bank.account.Account;
import com.florin.bank.account.AccountRepository;
import com.florin.bank.common.Money;
import com.florin.bank.config.BankProperties;
import com.florin.bank.customer.Customer;
import com.florin.bank.customer.CustomerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The only code that changes balances. Each method is one database transaction that locks what it
 * touches, checks every rule against the locked rows, then posts the ledger entries.
 *
 * <p>Lock order is the same everywhere: the paying customer's row, then accounts by ascending id. Two
 * money movements can therefore wait for each other but never in a cycle, so they cannot deadlock.
 *
 * <p>Isolation is READ COMMITTED. Once the customer lock is held, the daily-limit sum must see every
 * payment that committed before it. Under MySQL's default REPEATABLE READ, an earlier plain read in the
 * same transaction would pin a snapshot taken before the lock was granted, and the sum would miss them.
 */
@Service
public class Ledger {

    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final TransactionRepository transactions;
    private final LedgerEntryRepository entries;
    private final BankProperties bank;

    public Ledger(AccountRepository accounts, CustomerRepository customers, TransactionRepository transactions,
                  LedgerEntryRepository entries, BankProperties bank) {
        this.accounts = accounts;
        this.customers = customers;
        this.transactions = transactions;
        this.entries = entries;
        this.bank = bank;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse deposit(Long customerId, Long accountId, BigDecimal amount, String description,
                                       String idempotencyKey, String requestHash, Instant at) {
        requireWithinSingleLimit(amount);
        Account account = lockOwned(customerId, accountId);
        requireCanReceive(account);
        Transaction transaction = new Transaction(Transaction.Type.DEPOSIT, amount, account.getCurrency(), description,
                customers.getReferenceById(customerId), idempotencyKey, requestHash, at);
        transaction.post(account, LedgerEntry.Direction.CREDIT);
        return TransactionResponse.of(transactions.save(transaction), customerId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse withdraw(Long customerId, Long accountId, BigDecimal amount, String description,
                                        String idempotencyKey, String requestHash, Instant at) {
        requireWithinSingleLimit(amount);
        Customer customer = customers.lockById(customerId).orElseThrow();
        Account account = lockOwned(customerId, accountId);
        requireCanSend(account);
        requireWithinDailyLimit(customerId, amount, at);
        requireFunds(account, amount);
        Transaction transaction = new Transaction(Transaction.Type.WITHDRAWAL, amount, account.getCurrency(), description,
                customer, idempotencyKey, requestHash, at);
        transaction.post(account, LedgerEntry.Direction.DEBIT);
        return TransactionResponse.of(transactions.save(transaction), customerId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse transfer(Long customerId, Long fromAccountId, String toAccountNumber, BigDecimal amount,
                                        String description, String idempotencyKey, String requestHash, Instant at) {
        Long toAccountId = accounts.findIdByAccountNumber(toAccountNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No Florin account has that number"));
        if (toAccountId.equals(fromAccountId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a different account to send money to");
        }

        Customer customer = customers.lockById(customerId).orElseThrow();
        Map<Long, Account> locked = accounts.lockAllById(List.of(fromAccountId, toAccountId)).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        Account from = locked.get(fromAccountId);
        if (from == null || !from.isOwnedBy(customerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }
        Account to = locked.get(toAccountId);
        requireCanSend(from);
        requireCanReceive(to);
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Both accounts must use the same currency");
        }
        if (!to.isOwnedBy(customerId)) {
            // Moving money between your own accounts is unlimited; paying someone else is not.
            requireWithinSingleLimit(amount);
            requireWithinDailyLimit(customerId, amount, at);
        }
        requireFunds(from, amount);

        Transaction transaction = new Transaction(Transaction.Type.TRANSFER, amount, from.getCurrency(), description,
                customer, idempotencyKey, requestHash, at);
        transaction.post(from, LedgerEntry.Direction.DEBIT);
        transaction.post(to, LedgerEntry.Direction.CREDIT);
        return TransactionResponse.of(transactions.save(transaction), customerId);
    }

    /** The stored outcome of an earlier request with this key, for replaying it instead of moving money again. */
    @Transactional(readOnly = true)
    public Optional<StoredResult> findByIdempotencyKey(Long customerId, String idempotencyKey) {
        return transactions.findByInitiatedByIdAndIdempotencyKey(customerId, idempotencyKey)
                .map(found -> new StoredResult(found.getRequestHash(), TransactionResponse.of(found, customerId)));
    }

    @Transactional(readOnly = true)
    public TransactionResponse receipt(Long customerId, String reference) {
        return transactions.findByReference(reference.trim().toUpperCase(Locale.ROOT))
                .filter(transaction -> transaction.involves(customerId))
                .map(transaction -> TransactionResponse.of(transaction, customerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
    }

    @Transactional(readOnly = true)
    public LimitsResponse limits(Long customerId, Instant at) {
        ZoneId zone = bank.zone();
        LocalDate today = LocalDate.ofInstant(at, zone);
        BigDecimal daily = bank.limits().dailyOutgoing();
        BigDecimal used = outgoingOn(customerId, today);
        return new LimitsResponse(bank.limits().perTransaction(), daily, used, daily.subtract(used).max(Money.ZERO),
                today.plusDays(1).atStartOfDay(zone).toInstant());
    }

    private Account lockOwned(Long customerId, Long accountId) {
        return accounts.lockById(accountId)
                .filter(account -> account.isOwnedBy(customerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private static void requireCanSend(Account account) {
        switch (account.getStatus()) {
            case FROZEN -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This account is frozen. Unfreeze it to send money from it.");
            case CLOSED -> throw new ResponseStatusException(HttpStatus.CONFLICT, "This account is closed");
            case ACTIVE -> { }
        }
    }

    // A frozen account still receives money: freezing protects what is in it, not what comes in.
    private static void requireCanReceive(Account account) {
        if (account.getStatus() == Account.Status.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That account is closed and can't receive money");
        }
    }

    private static void requireFunds(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(account.getBalance());
        }
    }

    private void requireWithinSingleLimit(BigDecimal amount) {
        BigDecimal limit = bank.limits().perTransaction();
        if (amount.compareTo(limit) > 0) {
            throw new LimitExceededException(LimitExceededException.PER_TRANSACTION,
                    "A single payment can be at most " + Money.format(limit) + ".", limit, limit);
        }
    }

    private void requireWithinDailyLimit(Long customerId, BigDecimal amount, Instant at) {
        BigDecimal limit = bank.limits().dailyOutgoing();
        BigDecimal used = outgoingOn(customerId, LocalDate.ofInstant(at, bank.zone()));
        BigDecimal remaining = limit.subtract(used).max(Money.ZERO);
        if (amount.compareTo(remaining) > 0) {
            throw new LimitExceededException(LimitExceededException.DAILY,
                    "That would take you past your daily limit of " + Money.format(limit) + ". You can send up to "
                            + Money.format(remaining) + " more today.", limit, remaining);
        }
    }

    private BigDecimal outgoingOn(Long customerId, LocalDate day) {
        ZoneId zone = bank.zone();
        return Money.orZero(entries.outgoingBetween(customerId, day.atStartOfDay(zone).toInstant(),
                day.plusDays(1).atStartOfDay(zone).toInstant()));
    }

    /** What an idempotent replay needs: the original request's fingerprint and its response. */
    public record StoredResult(String requestHash, TransactionResponse response) {
    }
}
