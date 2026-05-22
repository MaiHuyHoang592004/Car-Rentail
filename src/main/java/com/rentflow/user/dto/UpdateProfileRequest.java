package com.rentflow.user.dto;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateProfileRequest(
        @Size(min = 1, max = 120, message = "Full name must be between 1 and 120 characters")
        String fullName,

        @Pattern(regexp = "^\\+?[0-9\\-\\s]{7,20}$", message = "Phone must be a valid phone number (7-20 digits, optional +, -, or spaces)")
        @Size(max = 30, message = "Phone must be at most 30 characters")
        String phone,

        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        @Size(max = 1000, message = "Address line must be at most 1000 characters")
        String addressLine
) {}
