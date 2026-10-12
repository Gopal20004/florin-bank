package com.florin.bank.account;

import java.math.BigDecimal;
import java.time.Instant;

/** Balance inquiry: the committed balance at {@code asOf}. */
public record BalanceResponse(Long accountId, String accountNumber, BigDecimal balance, String currency, Instant asOf) {
}
