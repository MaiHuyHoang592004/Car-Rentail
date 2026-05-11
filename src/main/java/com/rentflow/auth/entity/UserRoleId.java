package com.rentflow.auth.entity;

import java.io.Serializable;
import java.util.UUID;

public class UserRoleId implements Serializable {

    private UUID user;
    private Role role;

    public UserRoleId() {
    }

    public UserRoleId(UUID user, Role role) {
        this.user = user;
        this.role = role;
    }

    public UUID getUser() {
        return user;
    }

    public void setUser(UUID user) {
        this.user = user;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserRoleId that)) {
            return false;
        }
        return java.util.Objects.equals(user, that.user) && role == that.role;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(user, role);
    }
}
