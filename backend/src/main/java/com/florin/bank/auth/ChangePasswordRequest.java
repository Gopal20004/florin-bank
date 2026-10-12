package com.florin.bank.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Enter your current password") String currentPassword,
        @NotBlank(message = "Choose a new password")
        @Size(min = 8, max = 100, message = "Use 8 to 100 characters for your new password")
        String newPassword
) {
}
