package com.rentflow.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.common.web.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String code = "AUTH_INVALID_CREDENTIALS";
        String message = "Authentication required";

        if (authException != null) {
            if (authException.getMessage() != null
                    && authException.getMessage().contains("Access token has expired")) {
                code = "AUTH_TOKEN_EXPIRED";
                message = "Access token has expired";
            } else {
                try {
                    java.lang.reflect.Method m = authException.getClass().getMethod("getCode");
                    Object result = m.invoke(authException);
                    if (result instanceof String s) {
                        code = s;
                        message = authException.getMessage();
                    }
                } catch (Exception ignored) {
                }
            }
        }

        Object correlationId = request.getAttribute("correlationId");
        ErrorResponse error = ErrorResponse.of(code, message, correlationId instanceof String s ? s : null);

        HttpStatus status = request.getRequestURI().startsWith("/api/v1/host/")
                ? HttpStatus.FORBIDDEN
                : HttpStatus.UNAUTHORIZED;
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
