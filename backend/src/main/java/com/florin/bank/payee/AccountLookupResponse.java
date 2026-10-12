package com.florin.bank.payee;

/** Who an account number belongs to, masked, and whether it can be paid. */
public record AccountLookupResponse(String accountNumber, String holderName, boolean ownAccount, boolean acceptsPayments) {
}
