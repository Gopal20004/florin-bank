package com.florin.bank.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Rupee amounts are BigDecimal with exactly two decimal places everywhere in the code base. */
public final class Money {

    private Money() {
    }

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

    /** Request validation already allows at most two decimals, so this never has to round. */
    public static BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal orZero(BigDecimal amount) {
        return amount == null ? ZERO : normalize(amount);
    }

    /**
     * Indian digit grouping for messages, as in "₹1,23,456.00". java.text.DecimalFormat supports only
     * one group size, so it would print "₹123,456.00" for the en-IN locale.
     */
    public static String format(BigDecimal amount) {
        String plain = normalize(amount.abs()).toPlainString();
        int dot = plain.indexOf('.');
        String whole = plain.substring(0, dot);
        StringBuilder grouped = new StringBuilder();
        if (whole.length() <= 3) {
            grouped.append(whole);
        } else {
            String head = whole.substring(0, whole.length() - 3);
            for (int i = 0; i < head.length(); i++) {
                if (i > 0 && (head.length() - i) % 2 == 0) {
                    grouped.append(',');
                }
                grouped.append(head.charAt(i));
            }
            grouped.append(',').append(whole.substring(whole.length() - 3));
        }
        return (amount.signum() < 0 ? "-₹" : "₹") + grouped + plain.substring(dot);
    }
}
