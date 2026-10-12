package com.florin.bank.statement;

import com.florin.bank.transaction.LedgerEntry;
import com.florin.bank.transaction.PartyResponse;
import com.florin.bank.transaction.Transaction;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One line of a statement, from the point of view of the account it belongs to. {@code counterparty} is
 * the other side of a transfer, and null for deposits and withdrawals.
 */
public record StatementLine(
        Long entryId,
        Long transactionId,
        String reference,
        Transaction.Type type,
        LedgerEntry.Direction direction,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        AccountRef account,
        PartyResponse counterparty,
        Instant createdAt
) {

    /** Which of the customer's own accounts the line is on, for activity that spans all of them. */
    public record AccountRef(Long id, String nickname, String accountNumber) {
    }
}
