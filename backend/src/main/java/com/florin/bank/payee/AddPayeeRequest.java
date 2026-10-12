package com.florin.bank.payee;

import com.florin.bank.account.AccountNicknames;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code nickname} is optional; without one the payee is saved under the holder's masked name. */
public record AddPayeeRequest(
        @NotBlank(message = "Enter the payee's account number")
        @Size(max = 24, message = "That account number is too long")
        String accountNumber,

        @Size(max = 40, message = AccountNicknames.SIZE_MESSAGE)
        @Pattern(regexp = AccountNicknames.PATTERN, message = AccountNicknames.MESSAGE)
        String nickname
) {
}
