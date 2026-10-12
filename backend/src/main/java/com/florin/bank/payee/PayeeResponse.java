package com.florin.bank.payee;

import com.florin.bank.account.Account;
import com.florin.bank.common.Names;

import java.time.Instant;

/** {@code accountOpen} turns false if the payee closes the account; the payee stays saved but can't be paid. */
public record PayeeResponse(Long id, String nickname, String holderName, String accountNumber, boolean accountOpen,
                            Instant createdAt) {

    static PayeeResponse from(Payee payee) {
        Account account = payee.getAccount();
        return new PayeeResponse(payee.getId(), payee.getNickname(), Names.mask(account.getCustomer().getFullName()),
                account.getAccountNumber(), account.getStatus() != Account.Status.CLOSED, payee.getCreatedAt());
    }
}
