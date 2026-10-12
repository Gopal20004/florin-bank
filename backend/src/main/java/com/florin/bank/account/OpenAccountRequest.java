package com.florin.bank.account;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code nickname} is optional; without one the account is called "Savings" or "Current". */
public record OpenAccountRequest(
        @NotNull(message = "Choose a savings or current account") Account.Type type,
        @Size(min = 1, max = 40, message = AccountNicknames.SIZE_MESSAGE)
        @Pattern(regexp = AccountNicknames.PATTERN, message = AccountNicknames.MESSAGE)
        String nickname
) {
}
