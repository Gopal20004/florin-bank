package com.florin.bank.payee;

import com.florin.bank.account.AccountNicknames;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RenamePayeeRequest(
        @NotBlank(message = "Give the payee a name")
        @Size(max = 40, message = AccountNicknames.SIZE_MESSAGE)
        @Pattern(regexp = AccountNicknames.PATTERN, message = AccountNicknames.MESSAGE)
        String nickname
) {
}
