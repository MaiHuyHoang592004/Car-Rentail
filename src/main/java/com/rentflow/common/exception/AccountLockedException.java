package com.rentflow.common.exception;

import java.time.Instant;

public class AccountLockedException extends RentFlowException {

    private final Instant lockedUntil;

    public AccountLockedException(Instant lockedUntil) {
        super("AUTH_ACCOUNT_LOCKED",
                "Account is temporarily locked due to too many failed login attempts");
        this.lockedUntil = lockedUntil;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}
