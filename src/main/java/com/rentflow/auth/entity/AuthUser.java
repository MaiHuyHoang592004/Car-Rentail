package com.rentflow.auth.entity;

import com.rentflow.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "auth_users")
@Getter
@Setter
public class AuthUser extends BaseEntity {

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<UserRole> roles = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<RefreshToken> refreshTokens = new HashSet<>();

    public AuthUser() {
    }

    public AuthUser(String email, String passwordHash, UserStatus status, Boolean emailVerified) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = status != null ? status : UserStatus.ACTIVE;
        this.emailVerified = emailVerified != null ? emailVerified : false;
        this.roles = new HashSet<>();
        this.refreshTokens = new HashSet<>();
    }

    public void addRole(Role role) {
        UserRole ur = new UserRole();
        ur.setUser(this);
        ur.setRole(role);
        this.roles.add(ur);
    }

    public void clearRoles() {
        roles.clear();
    }
}
