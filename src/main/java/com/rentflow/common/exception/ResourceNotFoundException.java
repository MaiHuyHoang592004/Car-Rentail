package com.rentflow.common.exception;

public class ResourceNotFoundException extends RentFlowException {

    public ResourceNotFoundException(String resource, String id) {
        super("RESOURCE_NOT_FOUND", resource + " not found: " + id);
    }

    public ResourceNotFoundException(String code, String resource, String id) {
        super(code, resource + " not found: " + id);
    }
}
