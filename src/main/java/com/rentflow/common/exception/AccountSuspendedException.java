package com.rentflow.common.exception;

public class AccountSuspendedException extends RentFlowException {

    public AccountSuspendedException() {
        super("AUTH_ACCOUNT_SUSPENDED", "Account is suspended");
    }
}
