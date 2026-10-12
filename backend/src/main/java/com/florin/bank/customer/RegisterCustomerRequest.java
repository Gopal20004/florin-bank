package com.florin.bank.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterCustomerRequest(
        @NotBlank(message = "Enter your full name")
        @Size(min = 2, max = 100, message = "Use 2 to 100 characters for your name")
        @Pattern(regexp = CustomerNames.PATTERN, message = CustomerNames.MESSAGE)
        String fullName,

        @NotBlank(message = "Enter your email address")
        @Email(message = "Enter a valid email address")
        @Size(max = 255, message = "That email address is too long")
        String email,

        @NotBlank(message = "Choose a password")
        @Size(min = 8, max = 100, message = "Use 8 to 100 characters for your password")
        String password
) {
}
