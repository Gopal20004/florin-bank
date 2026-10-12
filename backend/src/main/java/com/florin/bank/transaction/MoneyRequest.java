package com.florin.bank.transaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** A deposit into or a withdrawal from one account. */
public record MoneyRequest(
        @NotNull(message = "Enter an amount")
        @DecimalMin(value = "0.01", message = "Enter an amount of at least ₹0.01")
        @Digits(integer = 13, fraction = 2, message = "Enter an amount with at most two decimal places")
        BigDecimal amount,

        @Size(max = 140, message = "Keep the note to 140 characters or fewer")
        String description
) {
}
