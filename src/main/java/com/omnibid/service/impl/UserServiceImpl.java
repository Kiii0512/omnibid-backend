package com.omnibid.service.impl;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.dto.storage.StorageUploadResult;
import com.omnibid.dto.user.ChangePasswordRequest;
import com.omnibid.dto.user.UpdateProfileRequest;
import com.omnibid.dto.user.UserProfileResponse;
import com.omnibid.entity.User;
import com.omnibid.repository.UserRepository;
import com.omnibid.service.StorageService;
import com.omnibid.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = getUser(userId);
        return mapToProfile(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getUser(userId);

        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.phoneNumber() != null) {
            user.setPhoneNumber(request.phoneNumber().isBlank() ? null : request.phoneNumber().trim());
        }
        if (request.address() != null) {
            user.setAddress(request.address().isBlank() ? null : request.address().trim());
        }

        User updated = userRepository.save(user);
        return mapToProfile(updated);
    }

    @Override
    @Transactional
    public UserProfileResponse updateAvatar(Long userId, MultipartFile file) {
        User user = getUser(userId);
        StorageUploadResult uploadResult = storageService.upload(file, "avatars/" + userId);
        user.setAvatarUrl(uploadResult.url());

        User updated = userRepository.save(user);
        return mapToProfile(updated);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getUser(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mật khẩu hiện tại không chính xác");
        }

        if (request.confirmPassword() == null || !request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mật khẩu xác nhận không khớp");
        }

        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mật khẩu mới không được trùng với mật khẩu cũ");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.increaseTokenVersion();
        userRepository.save(user);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found with id: " + userId));
    }

    private UserProfileResponse mapToProfile(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getAvatarUrl(),
                user.getAddress(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}
