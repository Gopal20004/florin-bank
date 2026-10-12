package com.florin.bank.transaction;

import java.math.BigDecimal;

public class LimitExceededException extends RuntimeException {

    public static final String PER_TRANSACTION = "PER_TRANSACTION_LIMIT";
    public static final String DAILY = "DAILY_LIMIT";

    private final String code;
    private final BigDecimal limit;
    private final BigDecimal remaining;

    public LimitExceededException(String code, String message, BigDecimal limit, BigDecimal remaining) {
        super(message);
        this.code = code;
        this.limit = limit;
        this.remaining = remaining;
    }

    public String getCode() { return code; }
    public BigDecimal getLimit() { return limit; }
    public BigDecimal getRemaining() { return remaining; }
}
