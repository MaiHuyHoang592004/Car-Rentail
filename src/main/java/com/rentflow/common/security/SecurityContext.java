package com.rentflow.common.security;

import com.rentflow.auth.entity.Role;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public interface SecurityContext {

    UUID currentUserId();

    boolean hasRole(Role role);

    void requireRole(Role role);

    void requireSelfOrAdmin(UUID userId);
}
