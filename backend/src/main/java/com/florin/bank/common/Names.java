package com.florin.bank.common;

public final class Names {

    private Names() {
    }

    /** Trimmed, with runs of whitespace collapsed, so "  Asha   Rao " is stored as "Asha Rao". */
    public static String normalize(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }

    /**
     * What one customer sees of another's name: the first name and the initial of the last, as in
     * "Asha R.". Enough to confirm the right person before paying them, without exposing the full name.
     */
    public static String mask(String fullName) {
        String[] parts = normalize(fullName).split(" ");
        if (parts.length == 1) {
            return parts[0];
        }
        String last = parts[parts.length - 1];
        return parts[0] + " " + last.substring(0, Character.charCount(last.codePointAt(0))) + ".";
    }
}
