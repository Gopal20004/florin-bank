package com.florin.bank.transaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Pays from one of your accounts to any open Florin account, yours or someone else's, by account number. */
public record TransferRequest(
        @NotNull(message = "Choose the account to pay from")
        Long fromAccountId,

        @NotBlank(message = "Enter the account number to pay")
        @Size(max = 24, message = "That account number is too long")
        String toAccountNumber,

        @NotNull(message = "Enter an amount")
        @DecimalMin(value = "0.01", message = "Enter an amount of at least ₹0.01")
        @Digits(integer = 13, fraction = 2, message = "Enter an amount with at most two decimal places")
        BigDecimal amount,

        @Size(max = 140, message = "Keep the note to 140 characters or fewer")
        String description
) {
}
