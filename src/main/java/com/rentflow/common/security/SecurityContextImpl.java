package com.rentflow.common.security;

import com.rentflow.auth.entity.Role;
import com.rentflow.common.exception.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SecurityContextImpl implements SecurityContext {

    @Override
    public UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUserId();
        }
        throw AuthenticationException.invalidCredentials();
    }

    @Override
    public boolean hasRole(Role role) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.hasRole(role);
        }
        return false;
    }

    @Override
    public void requireRole(Role role) {
        if (!hasRole(role)) {
            throw new com.rentflow.common.exception.AccessDeniedException();
        }
    }

    @Override
    public void requireSelfOrAdmin(UUID userId) {
        UUID currentId = currentUserId();
        if (!currentId.equals(userId) && !hasRole(Role.ADMIN)) {
            throw new com.rentflow.common.exception.AccessDeniedException();
        }
    }
}
