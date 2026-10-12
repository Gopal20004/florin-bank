package com.florin.bank.account;

import java.security.SecureRandom;

/**
 * Twelve-digit account numbers: a non-zero first digit, ten random digits and a Luhn check digit.
 * The check digit catches every single-digit typo and most swapped neighbours before any lookup runs.
 */
public final class AccountNumbers {

    private static final SecureRandom RANDOM = new SecureRandom();

    private AccountNumbers() {
    }

    public static String generate() {
        StringBuilder digits = new StringBuilder(12);
        digits.append(1 + RANDOM.nextInt(9));
        for (int i = 0; i < 10; i++) {
            digits.append(RANDOM.nextInt(10));
        }
        return digits.append(checkDigit(digits)).toString();
    }

    public static boolean isValid(String number) {
        return number != null && number.matches("\\d{12}")
                && checkDigit(number.substring(0, 11)) == number.charAt(11) - '0';
    }

    /** Accepts the number as people type it, "1234 5678 9012" or "1234-5678-9012", and returns the digits. */
    public static String clean(String input) {
        return input == null ? "" : input.replaceAll("[\\s-]", "");
    }

    /** "•••• 9012": enough to recognise an account without revealing it. */
    public static String mask(String number) {
        return "•••• " + number.substring(number.length() - 4);
    }

    /** The Luhn digit that makes {@code payload} followed by it pass the check. */
    static int checkDigit(CharSequence payload) {
        int sum = 0;
        // The payload's last digit sits next to the check digit, so it is the first one doubled.
        boolean doubled = true;
        for (int i = payload.length() - 1; i >= 0; i--) {
            int digit = payload.charAt(i) - '0';
            if (doubled) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubled = !doubled;
        }
        return (10 - sum % 10) % 10;
    }
}
