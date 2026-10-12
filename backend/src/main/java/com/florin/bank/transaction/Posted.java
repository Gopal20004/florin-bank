package com.florin.bank.transaction;

/** A completed money movement, and whether it was replayed from an earlier request with the same key. */
public record Posted(TransactionResponse transaction, boolean replayed) {
}
