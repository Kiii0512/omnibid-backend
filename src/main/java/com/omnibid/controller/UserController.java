package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.MessageResponse;
import com.omnibid.dto.user.AvatarResponse;
import com.omnibid.dto.user.ChangePasswordRequest;
import com.omnibid.dto.user.UpdateProfileRequest;
import com.omnibid.dto.user.UserProfileResponse;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        return ApiResponse.ok(userService.getProfile(currentUser.id()));
    }

    @PatchMapping("/me")
    public ApiResponse<UserProfileResponse> updateMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        return ApiResponse.ok(userService.updateProfile(currentUser.id(), request));
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AvatarResponse> uploadAvatar(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        return ApiResponse.ok(userService.updateAvatar(currentUser.id(), file));
    }

    @PatchMapping("/me/password")
    public ApiResponse<MessageResponse> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        userService.changePassword(currentUser.id(), request);
        return ApiResponse.ok(new MessageResponse("Đổi mật khẩu thành công, vui lòng đăng nhập lại"));
    }
}
