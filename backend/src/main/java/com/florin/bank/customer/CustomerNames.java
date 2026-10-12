package com.florin.bank.customer;

/** The one rule for names on a bank account, shared by sign-up and profile edits. */
final class CustomerNames {

    // Letters in any script, combining marks, spaces, apostrophes, dots and hyphens: "Ananya D'Souza-Rao".
    // Validation sees the name before it is tidied, so stray spaces around and inside it are allowed here.
    static final String PATTERN = "^\\s*[\\p{L}\\p{M}][\\p{L}\\p{M}'.\\s-]*$";
    static final String MESSAGE = "Use letters, spaces, apostrophes, dots or hyphens in your name";

    private CustomerNames() {
    }
}
