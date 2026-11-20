package com.omnibid.dto.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 255, message = "Full name cannot exceed 255 characters")
        String fullName,

        @Pattern(regexp = "^$|^[0-9]{9,15}$", message = "Phone number is invalid")
        String phoneNumber,

        @Size(max = 255, message = "Address cannot exceed 255 characters")
        String address,

        @Size(max = 500, message = "Avatar URL cannot exceed 500 characters")
        String avatarUrl
) {}

