package com.florin.bank.transaction;

import java.math.BigDecimal;
import java.time.Instant;

/** How much more the customer can send today, and when the daily allowance resets (midnight, bank time). */
public record LimitsResponse(
        BigDecimal perTransaction,
        BigDecimal dailyOutgoing,
        BigDecimal usedToday,
        BigDecimal remainingToday,
        Instant resetsAt
) {
}
