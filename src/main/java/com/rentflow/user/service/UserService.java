package com.rentflow.user.service;

import com.rentflow.auth.dto.AuthUserProfileResponse;
import com.rentflow.auth.dto.RegisterResponse;
import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.auth.repository.UserRoleRepository;
import com.rentflow.auth.service.AuthUserProfilePort;
import com.rentflow.common.exception.AccessDeniedException;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.user.dto.UpdateProfileRequest;
import com.rentflow.user.dto.UserProfileResponse;
import com.rentflow.user.dto.UserSummaryResponse;
import com.rentflow.user.entity.UserProfile;
import com.rentflow.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements AuthUserProfilePort {

    private final AuthUserRepository authUserRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserProfileRepository userProfileRepository;
    private final SecurityContext securityContext;

    @Override
    @Transactional
    public RegisterResponse createRegisteredProfile(
            AuthUser user, String fullName, List<Role> roles) {
        UserProfile profile = new UserProfile(fullName);
        profile.setUser(user);
        userProfileRepository.save(profile);
        return new RegisterResponse(
                user.getId(),
                user.getEmail(),
                roles.stream().map(Role::name).toList(),
                profile.getFullName(),
                user.getStatus().name(),
                profile.getDriverVerificationStatus().name());
    }

    @Override
    @Transactional(readOnly = true)
    public AuthUserProfileResponse getProfile(UUID userId, String email, List<Role> roles) {
        UserProfile profile = userProfileRepository.findByUserId(userId).orElseThrow();
        return new AuthUserProfileResponse(
                userId,
                email,
                roles.stream().map(Role::name).toList(),
                profile.getFullName(),
                profile.getPhone(),
                profile.getDateOfBirth(),
                profile.getAddressLine(),
                profile.getDriverVerificationStatus().name());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile() {
        UUID userId = securityContext.currentUserId();
        AuthUser user = authUserRepository.findById(userId).orElseThrow();
        List<Role> roles = userRoleRepository.findByUserId(userId)
                .stream().map(ur -> ur.getRole()).toList();
        UserProfile profile = userProfileRepository.findByUserId(userId).orElseThrow();

        return UserProfileResponse.from(userId, user.getEmail(), roles, profile);
    }

    @Transactional
    public UserProfileResponse updateCurrentUserProfile(UpdateProfileRequest request) {
        UUID userId = securityContext.currentUserId();
        AuthUser user = authUserRepository.findById(userId).orElseThrow();
        UserProfile profile = userProfileRepository.findByUserId(userId).orElseThrow();

        if (request.fullName() != null) {
            profile.setFullName(request.fullName());
        }
        if (request.phone() != null) {
            profile.setPhone(request.phone());
        }
        if (request.dateOfBirth() != null) {
            profile.setDateOfBirth(request.dateOfBirth());
        }
        if (request.addressLine() != null) {
            profile.setAddressLine(request.addressLine());
        }

        userProfileRepository.save(profile);

        List<Role> roles = userRoleRepository.findByUserId(userId)
                .stream().map(ur -> ur.getRole()).toList();

        log.info("Profile updated for user: {}", user.getEmail());

        return UserProfileResponse.from(userId, user.getEmail(), roles, profile);
    }

    @Transactional(readOnly = true)
    public Page<UserSummaryResponse> listUsers(UserStatus status, String role, Pageable pageable) {
        Role roleEnum = null;
        if (role != null && !role.isBlank()) {
            try {
                roleEnum = Role.valueOf(role.toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        Page<AuthUser> users = authUserRepository.findAllWithFilters(status, roleEnum, pageable);

        return users.map(user -> {
            UserProfile profile = userProfileRepository.findByUserId(user.getId()).orElse(null);
            List<Role> roles = userRoleRepository.findByUserId(user.getId())
                    .stream().map(ur -> ur.getRole()).toList();

            UserProfile actualProfile = profile != null ? profile : createMinimalProfile(user);
            return UserSummaryResponse.from(user, actualProfile, roles);
        });
    }

    private UserProfile createMinimalProfile(AuthUser user) {
        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setUserId(user.getId());
        profile.setFullName("Unknown");
        profile.setDriverVerificationStatus(UserProfile.DriverVerificationStatus.NOT_SUBMITTED);
        return profile;
    }
}
