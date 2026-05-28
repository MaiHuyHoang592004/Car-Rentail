package com.rentflow.common.exception;

public class EmailNotVerifiedException extends RentFlowException {

    public EmailNotVerifiedException() {
        super("EMAIL_NOT_VERIFIED", "Email is not verified");
    }
}
