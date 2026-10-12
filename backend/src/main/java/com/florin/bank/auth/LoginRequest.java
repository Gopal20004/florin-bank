package com.florin.bank.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Enter your email address") @Email(message = "Enter a valid email address") String email,
        @NotBlank(message = "Enter your password") String password
) {
}
