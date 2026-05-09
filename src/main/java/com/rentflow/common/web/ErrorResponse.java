package com.rentflow.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        List<FieldError> details,
        String correlationId
) {
    public static ErrorResponse of(String code, String message, String correlationId) {
        return new ErrorResponse(code, message, null, correlationId);
    }

    public static ErrorResponse of(String code, String message, List<FieldError> details, String correlationId) {
        return new ErrorResponse(code, message, details, correlationId);
    }

    public record FieldError(String field, String message) {}
}
