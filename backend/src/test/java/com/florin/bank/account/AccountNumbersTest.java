package com.florin.bank.account;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccountNumbersTest {

    @Test
    void checkDigitMatchesTheTextbookLuhnExample() {
        // 7992739871 -> 3 is the standard worked example of the Luhn algorithm.
        assertEquals(3, AccountNumbers.checkDigit("7992739871"));
    }

    @RepeatedTest(200)
    void generatedNumbersAreTwelveDigitsAndPassTheirOwnCheck() {
        String number = AccountNumbers.generate();
        assertTrue(number.matches("[1-9]\\d{11}"), number);
        assertTrue(AccountNumbers.isValid(number), number);
    }

    @RepeatedTest(50)
    void anySingleMistypedDigitIsCaught() {
        String number = AccountNumbers.generate();
        for (int position = 0; position < 12; position++) {
            for (char digit = '0'; digit <= '9'; digit++) {
                if (digit == number.charAt(position)) {
                    continue;
                }
                String typo = number.substring(0, position) + digit + number.substring(position + 1);
                assertFalse(AccountNumbers.isValid(typo), number + " -> " + typo);
            }
        }
    }

    @Test
    void numbersAreAcceptedAsPeopleTypeThem() {
        String number = AccountNumbers.generate();
        String spaced = number.substring(0, 4) + " " + number.substring(4, 8) + "-" + number.substring(8);
        assertEquals(number, AccountNumbers.clean(spaced));
        assertFalse(AccountNumbers.isValid(null));
        assertFalse(AccountNumbers.isValid("12345"));
        assertFalse(AccountNumbers.isValid("abcdefghijkl"));
    }

    @Test
    void maskingKeepsOnlyTheLastFourDigits() {
        assertEquals("•••• 9012", AccountNumbers.mask("123456789012"));
    }
}
