package com.rentflow.user.controller;

import com.rentflow.auth.dto.ChangePasswordRequest;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.service.PasswordService;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.user.dto.UpdateProfileRequest;
import com.rentflow.user.dto.UserProfileResponse;
import com.rentflow.user.dto.UserSummaryResponse;
import com.rentflow.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PasswordService passwordService;
    private final SecurityContext securityContext;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser() {
        return ResponseEntity.ok(userService.getCurrentUserProfile());
    }

    @PatchMapping("/me")
    public ResponseEntity<UserProfileResponse> updateCurrentUser(
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateCurrentUserProfile(request));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        passwordService.changePassword(
                securityContext.currentUserId(),
                request.currentPassword(),
                request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
