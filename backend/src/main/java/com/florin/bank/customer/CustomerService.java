package com.florin.bank.customer;

import com.florin.bank.account.Account;
import com.florin.bank.account.AccountService;
import com.florin.bank.common.Names;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
public class CustomerService {

    static final String EMAIL_TAKEN = "An account with this email already exists. Sign in instead.";

    private final CustomerRepository customers;
    private final AccountService accounts;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public CustomerService(CustomerRepository customers, AccountService accounts,
                           PasswordEncoder passwordEncoder, Clock clock) {
        this.customers = customers;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** Every new customer starts with an empty savings account, opened in the same transaction. */
    @Transactional
    public CustomerResponse register(RegisterCustomerRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (customers.existsByEmail(email)) {
            throw new CustomerAlreadyExistsException(EMAIL_TAKEN);
        }
        Instant now = clock.instant();
        Customer customer;
        try {
            customer = customers.save(new Customer(Names.normalize(request.fullName()), email,
                    passwordEncoder.encode(request.password()), now));
        } catch (DataIntegrityViolationException exception) {
            // Two sign-ups with one email can both pass the check above; the unique index decides.
            throw new CustomerAlreadyExistsException(EMAIL_TAKEN);
        }
        accounts.openAccount(customer, Account.Type.SAVINGS, "Everyday Savings", now);
        return CustomerResponse.from(customer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse profile(Long customerId) {
        return CustomerResponse.from(customers.findById(customerId).orElseThrow());
    }

    @Transactional
    public CustomerResponse updateProfile(Long customerId, UpdateProfileRequest request) {
        Customer customer = customers.findById(customerId).orElseThrow();
        customer.rename(Names.normalize(request.fullName()));
        return CustomerResponse.from(customer);
    }
}
