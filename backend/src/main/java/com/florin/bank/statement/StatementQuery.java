package com.florin.bank.statement;

import com.florin.bank.transaction.LedgerEntry;

import java.time.LocalDate;

/**
 * Optional filters for activity. {@code from} and {@code to} are inclusive calendar dates in the bank's
 * time zone; {@code text} matches the note or the exact transaction reference.
 */
public record StatementQuery(Long accountId, LocalDate from, LocalDate to, LedgerEntry.Direction direction, String text) {
}
