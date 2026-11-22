package com.omnibid.service;

import com.omnibid.dto.user.AvatarResponse;
import com.omnibid.dto.user.ChangePasswordRequest;
import com.omnibid.dto.user.UpdateProfileRequest;
import com.omnibid.dto.user.UserProfileResponse;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    UserProfileResponse getProfile(Long userId);

    UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request);

    AvatarResponse updateAvatar(Long userId, MultipartFile file);

    void changePassword(Long userId, ChangePasswordRequest request);
}
