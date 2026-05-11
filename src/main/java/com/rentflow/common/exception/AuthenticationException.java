package com.rentflow.common.exception;

public class AuthenticationException extends RuntimeException {

    private final String code;

    public AuthenticationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static AuthenticationException invalidCredentials() {
        return new AuthenticationException("AUTH_INVALID_CREDENTIALS", "Invalid email or password");
    }

    public static AuthenticationException tokenExpired() {
        return new AuthenticationException("AUTH_TOKEN_EXPIRED", "Access token has expired");
    }

    public static AuthenticationException invalidToken() {
        return new AuthenticationException("AUTH_INVALID_CREDENTIALS", "Invalid or tampered access token");
    }
}
