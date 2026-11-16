package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.dto.user.ChangePasswordRequest;
import com.omnibid.dto.user.UpdateProfileRequest;
import com.omnibid.dto.user.UserProfileResponse;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        UserProfileResponse response = userService.getProfile(currentUser.id());
        return ApiResponse.success(response);
    }

    @PatchMapping("/me")
    public ApiResponse<UserProfileResponse> updateMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        UserProfileResponse response = userService.updateProfile(currentUser.id(), request);
        return ApiResponse.success("Profile updated successfully", response);
    }

    @PostMapping("/me/avatar")
    public ApiResponse<UserProfileResponse> uploadAvatarPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("file") MultipartFile file
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        UserProfileResponse response = userService.updateAvatar(currentUser.id(), file);
        return ApiResponse.success("Avatar updated successfully", response);
    }

    @PatchMapping("/me/avatar")
    public ApiResponse<UserProfileResponse> uploadAvatarPatch(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("file") MultipartFile file
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        UserProfileResponse response = userService.updateAvatar(currentUser.id(), file);
        return ApiResponse.success("Avatar updated successfully", response);
    }

    @PatchMapping("/me/password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        userService.changePassword(currentUser.id(), request);
        return ApiResponse.success("Password changed successfully", null);
    }
}

