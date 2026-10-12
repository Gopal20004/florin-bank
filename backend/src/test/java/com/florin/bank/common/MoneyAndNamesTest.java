package com.florin.bank.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyAndNamesTest {

    @Test
    void amountsUseIndianDigitGrouping() {
        assertEquals("₹0.50", Money.format(new BigDecimal("0.5")));
        assertEquals("₹999.00", Money.format(new BigDecimal("999")));
        assertEquals("₹1,000.00", Money.format(new BigDecimal("1000")));
        assertEquals("₹12,345.67", Money.format(new BigDecimal("12345.67")));
        assertEquals("₹1,23,456.00", Money.format(new BigDecimal("123456")));
        assertEquals("₹12,34,567.89", Money.format(new BigDecimal("1234567.89")));
        assertEquals("₹1,00,00,000.00", Money.format(new BigDecimal("10000000")));
        assertEquals("-₹2,500.00", Money.format(new BigDecimal("-2500")));
    }

    @Test
    void namesAreTidiedAndMaskedForOtherCustomers() {
        assertEquals("Asha Rao", Names.normalize("  Asha   Rao "));
        assertEquals("Asha R.", Names.mask("Asha Rao"));
        assertEquals("Asha R.", Names.mask("Asha Devi Rao"));
        assertEquals("Prince", Names.mask("Prince"));
        assertEquals("Élodie Ø.", Names.mask("Élodie Østergaard"));
    }
}
