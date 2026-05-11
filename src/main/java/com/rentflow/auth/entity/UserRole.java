package com.rentflow.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "user_roles", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "role"})
})
@IdClass(UserRoleId.class)
@Getter
@Setter
public class UserRole {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AuthUser user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UserRole() {
        this.createdAt = Instant.now();
    }

    public UserRole(AuthUser user, Role role) {
        this.user = user;
        this.role = role;
        this.createdAt = Instant.now();
    }
}
