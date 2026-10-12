package com.florin.bank.transaction;

import com.florin.bank.common.Money;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {

    private final BigDecimal available;

    public InsufficientFundsException(BigDecimal available) {
        super("Not enough money in this account. Available balance: " + Money.format(available) + ".");
        this.available = available;
    }

    public BigDecimal getAvailable() {
        return available;
    }
}
