package com.rentflow.common.exception;

import lombok.Getter;

@Getter
public abstract class RentFlowException extends RuntimeException {

    private final String code;

    protected RentFlowException(String code, String message) {
        super(message);
        this.code = code;
    }

    protected RentFlowException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
