package com.florin.bank.transaction;

import java.security.SecureRandom;

/** Customer-facing transaction references such as "FLR7K2QX9MPAD". */
final class References {

    // Crockford's base 32 has no I, L, O or U, so a reference read out over the phone is unambiguous.
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private References() {
    }

    /** 10 random symbols: 2^50 possibilities, so a collision (caught by the unique index) is practically impossible. */
    static String next() {
        char[] symbols = new char[10];
        for (int i = 0; i < symbols.length; i++) {
            symbols[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return "FLR" + new String(symbols);
    }
}
