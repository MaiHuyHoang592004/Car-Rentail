package com.rentflow.common.exception;

public class AccessDeniedException extends RentFlowException {

    public AccessDeniedException() {
        super("ACCESS_DENIED", "Access denied: insufficient permissions");
    }
}
