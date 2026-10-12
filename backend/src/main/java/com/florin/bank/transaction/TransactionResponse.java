package com.florin.bank.transaction;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A receipt. {@code from} is null for deposits (cash in) and {@code to} is null for withdrawals (cash out).
 */
public record TransactionResponse(
        Long id,
        String reference,
        Transaction.Type type,
        BigDecimal amount,
        String currency,
        String description,
        PartyResponse from,
        PartyResponse to,
        Instant createdAt
) {

    static TransactionResponse of(Transaction transaction, Long viewerId) {
        PartyResponse from = null;
        PartyResponse to = null;
        for (LedgerEntry entry : transaction.getEntries()) {
            PartyResponse party = PartyResponse.of(entry.getAccount(), viewerId);
            if (entry.getDirection() == LedgerEntry.Direction.DEBIT) {
                from = party;
            } else {
                to = party;
            }
        }
        return new TransactionResponse(transaction.getId(), transaction.getReference(), transaction.getType(),
                transaction.getAmount(), transaction.getCurrency(), transaction.getDescription(), from, to,
                transaction.getCreatedAt());
    }
}
