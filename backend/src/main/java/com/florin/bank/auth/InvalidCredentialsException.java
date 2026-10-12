package com.florin.bank.auth;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("That email and password do not match our records");
    }
}
