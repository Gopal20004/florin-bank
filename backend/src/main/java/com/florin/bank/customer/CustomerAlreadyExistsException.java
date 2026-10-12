package com.florin.bank.customer;

public class CustomerAlreadyExistsException extends RuntimeException {

    public CustomerAlreadyExistsException(String message) {
        super(message);
    }
}
