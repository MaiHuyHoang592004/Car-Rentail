package com.rentflow.auth.service;

import java.time.Instant;

public interface EmailDeliveryService {

    void sendVerificationEmail(String to, String rawToken, Instant expiresAt);
}
