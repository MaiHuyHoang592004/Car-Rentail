package com.rentflow.common.security;

import java.util.UUID;

public interface EmailVerificationPolicy {

    void requireVerifiedEmail(UUID userId);
}
