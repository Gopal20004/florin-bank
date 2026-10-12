package com.florin.bank.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Enter your full name")
        @Size(min = 2, max = 100, message = "Use 2 to 100 characters for your name")
        @Pattern(regexp = CustomerNames.PATTERN, message = CustomerNames.MESSAGE)
        String fullName
) {
}
