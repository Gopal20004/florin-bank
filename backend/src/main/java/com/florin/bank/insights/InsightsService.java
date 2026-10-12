package com.florin.bank.insights;

import com.florin.bank.account.AccountRepository;
import com.florin.bank.common.Money;
import com.florin.bank.config.BankProperties;
import com.florin.bank.transaction.LedgerEntry;
import com.florin.bank.transaction.LedgerEntryRepository;
import com.florin.bank.transaction.LedgerMovement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class InsightsService {

    private final AccountRepository accounts;
    private final LedgerEntryRepository entries;
    private final BankProperties bank;
    private final Clock clock;

    public InsightsService(AccountRepository accounts, LedgerEntryRepository entries, BankProperties bank, Clock clock) {
        this.accounts = accounts;
        this.entries = entries;
        this.bank = bank;
        this.clock = clock;
    }

    /**
     * Walks back from today's total: each day's closing balance is the next day's minus that next day's
     * net movement. One query loads the movements; nothing is stored or precomputed.
     */
    @Transactional(readOnly = true)
    public InsightsResponse summary(Long customerId, int days) {
        ZoneId zone = bank.zone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate firstDay = today.minusDays(days - 1L);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate previousMonthStart = monthStart.minusMonths(1);
        LocalDate earliest = firstDay.isBefore(previousMonthStart) ? firstDay : previousMonthStart;

        List<LedgerMovement> movements = entries.movementsSince(customerId, earliest.atStartOfDay(zone).toInstant());
        BigDecimal total = Money.orZero(accounts.totalBalance(customerId));

        // Both legs of a transfer between two of the customer's accounts appear here; nothing else does.
        Set<Long> internal = movements.stream()
                .collect(Collectors.groupingBy(LedgerMovement::transactionId, Collectors.counting()))
                .entrySet().stream()
                .filter(group -> group.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        Map<LocalDate, BigDecimal> netByDay = new HashMap<>();
        BigDecimal monthIn = Money.ZERO;
        BigDecimal monthOut = Money.ZERO;
        BigDecimal previousIn = Money.ZERO;
        BigDecimal previousOut = Money.ZERO;
        for (LedgerMovement movement : movements) {
            LocalDate day = LocalDate.ofInstant(movement.createdAt(), zone);
            boolean credit = movement.direction() == LedgerEntry.Direction.CREDIT;
            netByDay.merge(day, credit ? movement.amount() : movement.amount().negate(), BigDecimal::add);
            if (internal.contains(movement.transactionId())) {
                continue;
            }
            if (!day.isBefore(monthStart)) {
                if (credit) {
                    monthIn = monthIn.add(movement.amount());
                } else {
                    monthOut = monthOut.add(movement.amount());
                }
            } else if (!day.isBefore(previousMonthStart)) {
                if (credit) {
                    previousIn = previousIn.add(movement.amount());
                } else {
                    previousOut = previousOut.add(movement.amount());
                }
            }
        }

        List<InsightsResponse.BalancePoint> history = new ArrayList<>(days);
        BigDecimal closing = total;
        for (LocalDate day = today; !day.isBefore(firstDay); day = day.minusDays(1)) {
            history.add(new InsightsResponse.BalancePoint(day, closing));
            closing = closing.subtract(netByDay.getOrDefault(day, Money.ZERO));
        }
        Collections.reverse(history);

        return new InsightsResponse(bank.currency(), total, monthIn, monthOut, previousIn, previousOut, history);
    }
}
