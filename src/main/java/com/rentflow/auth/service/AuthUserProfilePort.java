package com.rentflow.auth.service;

import com.rentflow.auth.dto.AuthUserProfileResponse;
import com.rentflow.auth.dto.RegisterResponse;
import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.Role;

import java.util.List;
import java.util.UUID;

public interface AuthUserProfilePort {

    RegisterResponse createRegisteredProfile(AuthUser user, String fullName, List<Role> roles);

    AuthUserProfileResponse getProfile(UUID userId, String email, List<Role> roles);
}
