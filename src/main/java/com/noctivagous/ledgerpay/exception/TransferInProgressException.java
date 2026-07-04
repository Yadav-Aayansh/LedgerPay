package com.noctivagous.ledgerpay.exception;

public class TransferInProgressException extends RuntimeException {

    public TransferInProgressException(String idempotencyKey) {
        super("A request with idempotency key " + idempotencyKey + " is already being processed");
    }
}
