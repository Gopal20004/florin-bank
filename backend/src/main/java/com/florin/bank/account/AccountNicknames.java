package com.florin.bank.account;

/** Nicknames for accounts and payees: "Rainy Day Fund", "Rent & bills", "Kabir · Rent", "Studio No. 2". */
public final class AccountNicknames {

    public static final String PATTERN = "^\\s*[\\p{L}\\p{N}][\\p{L}\\p{M}\\p{N}\\s'&.,()/#+:·-]*$";
    public static final String MESSAGE = "Use letters, numbers, spaces and simple punctuation in the name";
    public static final String SIZE_MESSAGE = "Keep the name to 40 characters or fewer";

    private AccountNicknames() {
    }
}
