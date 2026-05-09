package com.rentflow.common.exception;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class CorrelationIdHelper {

    private static final String HEADER_NAME = "X-Correlation-Id";
    private static final String ATTRIBUTE_NAME = "correlationId";

    public String getCorrelationId() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        return (String) attrs.getRequest().getAttribute(ATTRIBUTE_NAME);
    }

    public String getOrGenerate() {
        String id = getCorrelationId();
        return id != null ? id : "unknown";
    }

    public void setCorrelationId(String correlationId) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            attrs.setAttribute(ATTRIBUTE_NAME, correlationId, ServletRequestAttributes.SCOPE_REQUEST);
        }
    }
}
