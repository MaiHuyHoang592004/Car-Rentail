package com.rentflow.common.exception;

public class BusinessRuleException extends RentFlowException {

    public BusinessRuleException(String code, String message) {
        super(code, message);
    }
}
