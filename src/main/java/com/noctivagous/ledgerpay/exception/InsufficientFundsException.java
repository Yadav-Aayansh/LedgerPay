package com.noctivagous.ledgerpay.exception;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(Long accountId) {
        super("Insufficient balance in account " + accountId);
    }
}
