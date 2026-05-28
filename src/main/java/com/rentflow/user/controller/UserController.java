package com.rentflow.user.controller;

import com.rentflow.auth.dto.ChangePasswordRequest;
import com.rentflow.auth.service.EmailVerificationService;
import com.rentflow.auth.service.PasswordService;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.user.dto.DriverVerificationResponse;
import com.rentflow.user.dto.SubmitDriverLicenseRequest;
import com.rentflow.user.dto.UpdateProfileRequest;
import com.rentflow.user.dto.UserProfileResponse;
import com.rentflow.user.service.DriverVerificationService;
import com.rentflow.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PasswordService passwordService;
    private final EmailVerificationService emailVerificationService;
    private final DriverVerificationService driverVerificationService;
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

    @PostMapping("/me/resend-verification")
    public ResponseEntity<Void> resendVerification() {
        emailVerificationService.sendVerification(securityContext.currentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/driver-license")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<DriverVerificationResponse> submitDriverLicense(
            @Valid @RequestBody SubmitDriverLicenseRequest request) {
        DriverVerificationResponse response = driverVerificationService.submit(securityContext.currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
