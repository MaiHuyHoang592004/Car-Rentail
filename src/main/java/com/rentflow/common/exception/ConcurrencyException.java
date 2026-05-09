package com.rentflow.common.exception;

public class ConcurrencyException extends RentFlowException {

    public ConcurrencyException(String message) {
        super("CONCURRENCY_ERROR", message);
    }
}
