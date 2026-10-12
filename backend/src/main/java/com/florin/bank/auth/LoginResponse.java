package com.florin.bank.auth;

import java.time.Instant;

public record LoginResponse(String token, Instant expiresAt) {
}
