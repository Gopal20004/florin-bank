package com.florin.bank.payee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountLookupRequest(
        @NotBlank(message = "Enter an account number")
        @Size(max = 24, message = "That account number is too long")
        String accountNumber
) {
}
