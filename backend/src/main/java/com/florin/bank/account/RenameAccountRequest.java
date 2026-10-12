package com.florin.bank.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RenameAccountRequest(
        @NotBlank(message = "Give the account a name")
        @Size(max = 40, message = AccountNicknames.SIZE_MESSAGE)
        @Pattern(regexp = AccountNicknames.PATTERN, message = AccountNicknames.MESSAGE)
        String nickname
) {
}
