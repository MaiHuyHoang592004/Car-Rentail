package com.rentflow.auth.repository;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuthUserRepository extends JpaRepository<AuthUser, UUID> {

    Optional<AuthUser> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM AuthUser u WHERE " +
           "(:status IS NULL OR u.status = :status) " +
           "AND (:hasRole IS NULL OR EXISTS (SELECT r FROM u.roles r WHERE r.role = :hasRole))")
    Page<AuthUser> findAllWithFilters(
            @Param("status") UserStatus status,
            @Param("hasRole") com.rentflow.auth.entity.Role hasRole,
            Pageable pageable);

    @Query("SELECT u.status FROM AuthUser u WHERE u.id = :id")
    Optional<UserStatus> findStatusById(@Param("id") UUID id);

    @Query("""
            SELECT DISTINCT u.id
            FROM AuthUser u
            JOIN u.roles r
            WHERE r.role = :role
              AND u.status = :status
            """)
    List<UUID> findUserIdsByRoleAndStatus(
            @Param("role") Role role,
            @Param("status") UserStatus status);
}
