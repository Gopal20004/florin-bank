package com.florin.bank.transaction;

import java.math.BigDecimal;
import java.time.Instant;

/** The few columns of a ledger entry that insights need, read without loading entities. */
public record LedgerMovement(Long transactionId, LedgerEntry.Direction direction, BigDecimal amount, Instant createdAt) {
}
