package com.florin.bank.payee;

import com.florin.bank.account.Account;
import com.florin.bank.account.AccountNumbers;
import com.florin.bank.account.AccountRepository;
import com.florin.bank.common.Names;
import com.florin.bank.config.BankProperties;
import com.florin.bank.customer.Customer;
import com.florin.bank.customer.CustomerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;

@Service
public class PayeeService {

    private final PayeeRepository payees;
    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final BankProperties bank;
    private final Clock clock;

    public PayeeService(PayeeRepository payees, AccountRepository accounts, CustomerRepository customers,
                        BankProperties bank, Clock clock) {
        this.payees = payees;
        this.accounts = accounts;
        this.customers = customers;
        this.bank = bank;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PayeeResponse> list(Long customerId) {
        return payees.findByCustomerIdOrderByNicknameAsc(customerId).stream().map(PayeeResponse::from).toList();
    }

    /** Shows whose account a number belongs to before anyone pays it, as banks confirm a payee's registered name. */
    @Transactional(readOnly = true)
    public AccountLookupResponse lookup(Long customerId, String accountNumber) {
        Account account = find(accountNumber);
        return new AccountLookupResponse(account.getAccountNumber(), Names.mask(account.getCustomer().getFullName()),
                account.isOwnedBy(customerId), account.getStatus() != Account.Status.CLOSED);
    }

    @Transactional
    public PayeeResponse add(Long customerId, AddPayeeRequest request) {
        // Count-then-insert under the customer's row lock, so the payee cap holds under concurrent requests.
        Customer customer = customers.lockById(customerId).orElseThrow();
        Account account = find(request.accountNumber());
        if (account.isOwnedBy(customerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "That is one of your own accounts. Move money between your accounts without saving a payee.");
        }
        if (account.getStatus() == Account.Status.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That account is closed and can't receive money");
        }
        payees.findByCustomerIdAndAccountId(customerId, account.getId()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You already saved this account as “" + existing.getNickname() + "”");
        });
        int max = bank.limits().maxPayees();
        if (payees.countByCustomerId(customerId) >= max) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You can save up to " + max + " payees. Remove one to add another.");
        }
        String nickname = request.nickname() == null || request.nickname().isBlank()
                ? Names.mask(account.getCustomer().getFullName())
                : Names.normalize(request.nickname());
        return PayeeResponse.from(payees.save(new Payee(customer, account, nickname, clock.instant())));
    }

    @Transactional
    public PayeeResponse rename(Long customerId, Long payeeId, RenamePayeeRequest request) {
        Payee payee = owned(customerId, payeeId);
        payee.rename(Names.normalize(request.nickname()));
        return PayeeResponse.from(payee);
    }

    @Transactional
    public void remove(Long customerId, Long payeeId) {
        payees.delete(owned(customerId, payeeId));
    }

    private Payee owned(Long customerId, Long payeeId) {
        return payees.findByIdAndCustomerId(payeeId, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payee not found"));
    }

    private Account find(String rawAccountNumber) {
        String number = AccountNumbers.clean(rawAccountNumber);
        if (!AccountNumbers.isValid(number)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "That account number doesn't look right. Check all 12 digits and try again.");
        }
        return accounts.findWithCustomerByAccountNumber(number)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No Florin account has that number"));
    }
}
