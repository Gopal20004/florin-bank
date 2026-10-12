package com.florin.bank.account;

import com.florin.bank.common.Names;
import com.florin.bank.config.BankProperties;
import com.florin.bank.customer.Customer;
import com.florin.bank.customer.CustomerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final BankProperties bank;
    private final Clock clock;

    public AccountService(AccountRepository accounts, CustomerRepository customers, BankProperties bank, Clock clock) {
        this.accounts = accounts;
        this.customers = customers;
        this.bank = bank;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(Long customerId) {
        return accounts.findByCustomerIdOrderByIdAsc(customerId).stream().map(AccountResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(Long customerId, Long accountId) {
        return AccountResponse.from(owned(customerId, accountId));
    }

    @Transactional(readOnly = true)
    public BalanceResponse balance(Long customerId, Long accountId) {
        Account account = owned(customerId, accountId);
        return new BalanceResponse(account.getId(), account.getAccountNumber(), account.getBalance(),
                account.getCurrency(), clock.instant());
    }

    @Transactional
    public AccountResponse open(Long customerId, OpenAccountRequest request) {
        // Count-then-insert under the customer's row lock: two simultaneous requests cannot both squeeze under the cap.
        Customer customer = customers.lockById(customerId).orElseThrow();
        int max = bank.limits().maxAccounts();
        if (accounts.countByCustomerIdAndStatusNot(customerId, Account.Status.CLOSED) >= max) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You can keep up to " + max + " open accounts. Close one to open another.");
        }
        return AccountResponse.from(openAccount(customer, request.type(), request.nickname(), clock.instant()));
    }

    /** Opens an empty account. Callers run inside a transaction (registration, {@link #open}, the demo seeder). */
    public Account openAccount(Customer customer, Account.Type type, String nickname, Instant openedAt) {
        String name = nickname == null || nickname.isBlank() ? defaultNickname(type) : Names.normalize(nickname);
        for (int attempt = 0; attempt < 5; attempt++) {
            String number = AccountNumbers.generate();
            // The unique index is the real guarantee; this check only avoids a doomed insert.
            if (!accounts.existsByAccountNumber(number)) {
                return accounts.save(new Account(customer, number, type, name, bank.currency(), openedAt));
            }
        }
        throw new IllegalStateException("Could not allocate a free account number");
    }

    @Transactional
    public AccountResponse rename(Long customerId, Long accountId, RenameAccountRequest request) {
        Account account = owned(customerId, accountId);
        requireNotClosed(account);
        account.rename(Names.normalize(request.nickname()));
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse freeze(Long customerId, Long accountId) {
        Account account = lockOwned(customerId, accountId);
        requireNotClosed(account);
        account.freeze();
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse unfreeze(Long customerId, Long accountId) {
        Account account = lockOwned(customerId, accountId);
        requireNotClosed(account);
        account.unfreeze();
        return AccountResponse.from(account);
    }

    /** Closing keeps the account and its history; it only stops new money moving in or out. */
    @Transactional
    public AccountResponse close(Long customerId, Long accountId) {
        customers.lockById(customerId).orElseThrow();
        Account account = lockOwned(customerId, accountId);
        requireNotClosed(account);
        // Checked first: telling someone to move the balance "to another account" makes no sense if there is none.
        if (accounts.countByCustomerIdAndStatusNot(customerId, Account.Status.CLOSED) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This is your only open account, so it can't be closed");
        }
        if (account.getBalance().signum() != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Move the remaining balance to another account before closing this one");
        }
        account.close(clock.instant());
        return AccountResponse.from(account);
    }

    private Account owned(Long customerId, Long accountId) {
        return accounts.findByIdAndCustomerId(accountId, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    // Another customer's account answers 404 like a missing one, so ids cannot be probed.
    private Account lockOwned(Long customerId, Long accountId) {
        return accounts.lockById(accountId)
                .filter(account -> account.isOwnedBy(customerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private static void requireNotClosed(Account account) {
        if (account.getStatus() == Account.Status.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This account is closed");
        }
    }

    private static String defaultNickname(Account.Type type) {
        return type == Account.Type.SAVINGS ? "Savings" : "Current";
    }
}
