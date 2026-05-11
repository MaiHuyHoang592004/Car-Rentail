package com.rentflow.common.exception;

public class BusinessRuleViolationException extends RentFlowException {

    public BusinessRuleViolationException(String code, String message) {
        super(code, message);
    }
}
