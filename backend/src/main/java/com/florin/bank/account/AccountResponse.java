package com.florin.bank.account;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        Long id,
        String accountNumber,
        Account.Type type,
        String nickname,
        BigDecimal balance,
        String currency,
        Account.Status status,
        Instant openedAt,
        Instant closedAt
) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getAccountNumber(), account.getType(), account.getNickname(),
                account.getBalance(), account.getCurrency(), account.getStatus(), account.getOpenedAt(), account.getClosedAt());
    }
}
