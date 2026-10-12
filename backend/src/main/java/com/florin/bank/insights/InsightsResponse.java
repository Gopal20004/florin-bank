package com.florin.bank.insights;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The overview dashboard. Money in and out leave out transfers between the customer's own accounts:
 * moving savings around is neither income nor spending. {@code history} holds one closing balance per
 * day (bank time), oldest first, ending today.
 */
public record InsightsResponse(
        String currency,
        BigDecimal totalBalance,
        BigDecimal monthIn,
        BigDecimal monthOut,
        BigDecimal previousMonthIn,
        BigDecimal previousMonthOut,
        List<BalancePoint> history
) {

    public record BalancePoint(LocalDate date, BigDecimal balance) {
    }
}
