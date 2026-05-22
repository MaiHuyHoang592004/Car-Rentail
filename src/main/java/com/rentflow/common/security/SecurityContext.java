package com.rentflow.common.security;

import com.rentflow.auth.entity.Role;

import java.util.UUID;

public interface SecurityContext {

    UUID currentUserId();

    boolean hasRole(Role role);

    void requireRole(Role role);

    void requireSelfOrAdmin(UUID userId);
}
