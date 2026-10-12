package com.florin.bank.auth;

/** Reads the token out of an "Authorization: Bearer ..." header value. */
public final class BearerToken {

    private static final String PREFIX = "Bearer ";

    private BearerToken() {
    }

    /** The token, or null when the header is missing, of another scheme, or blank. */
    public static String from(String authorization) {
        if (authorization == null || !authorization.startsWith(PREFIX)) {
            return null;
        }
        String token = authorization.substring(PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
