package com.florin.bank.auth;

import java.time.Instant;

/** A signed-in device. {@code current} marks the session that made this request. */
public record SessionResponse(Long id, String userAgent, Instant createdAt, Instant expiresAt, boolean current) {
}
