package com.rentflow.user.service;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserRole;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.auth.repository.UserRoleRepository;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.user.dto.UserSummaryResponse;
import com.rentflow.user.entity.UserProfile;
import com.rentflow.user.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private AuthUserRepository authUserRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private SecurityContext securityContext;

    @Test
    void listUsersFetchesProfilesAndRolesInBatch() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        AuthUser user1 = new AuthUser("one@example.com", "hash", UserStatus.ACTIVE, true);
        user1.setId(userId1);
        AuthUser user2 = new AuthUser("two@example.com", "hash", UserStatus.ACTIVE, true);
        user2.setId(userId2);

        Pageable pageable = PageRequest.of(0, 20);
        Page<AuthUser> page = new PageImpl<>(List.of(user1, user2), pageable, 2);
        when(authUserRepository.findAllWithFilters(UserStatus.ACTIVE, Role.CUSTOMER, pageable)).thenReturn(page);

        UserProfile profile1 = new UserProfile("User One");
        profile1.setUserId(userId1);
        profile1.setDriverVerificationStatus(UserProfile.DriverVerificationStatus.APPROVED);
        when(userProfileRepository.findByUserIdIn(List.of(userId1, userId2))).thenReturn(List.of(profile1));

        UserRole role1 = new UserRole(user1, Role.CUSTOMER);
        UserRole role2 = new UserRole(user2, Role.HOST);
        when(userRoleRepository.findByUserIdIn(List.of(userId1, userId2))).thenReturn(List.of(role1, role2));

        UserService service = new UserService(
                authUserRepository,
                userRoleRepository,
                userProfileRepository,
                securityContext);

        Page<UserSummaryResponse> result = service.listUsers(UserStatus.ACTIVE, Role.CUSTOMER, pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).fullName()).isEqualTo("User One");
        assertThat(result.getContent().get(1).fullName()).isEqualTo("Unknown");
        verify(userProfileRepository).findByUserIdIn(eq(List.of(userId1, userId2)));
        verify(userRoleRepository).findByUserIdIn(eq(List.of(userId1, userId2)));
        verify(userProfileRepository, never()).findByUserId(any());
        verify(userRoleRepository, never()).findByUserId(any());
    }
}
