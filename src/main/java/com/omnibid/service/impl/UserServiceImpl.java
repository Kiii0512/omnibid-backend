package com.omnibid.service.impl;

import com.omnibid.common.AfterCommitExecutor;
import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.ImageValidator;
import com.omnibid.dto.storage.StoredImage;
import com.omnibid.dto.user.ChangePasswordRequest;
import com.omnibid.dto.user.UpdateProfileRequest;
import com.omnibid.dto.user.UserProfileResponse;
import com.omnibid.entity.User;
import com.omnibid.repository.UserRepository;
import com.omnibid.service.ImageStorageService;
import com.omnibid.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ImageStorageService imageStorageService;
    private final AfterCommitExecutor afterCommitExecutor;
    private final TransactionTemplate transactionTemplate;

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
    public String updateAvatar(Long userId, MultipartFile file) {
        ImageValidator.ValidatedImage val = ImageValidator.validate(file);
        StoredImage stored = imageStorageService.upload(val.bytes(), "omnibid/avatars");

        try {
            return transactionTemplate.execute(status -> {
                User user = getUser(userId);
                String oldPublicId = user.getAvatarPublicId();

                user.setAvatarUrl(stored.url());
                user.setAvatarPublicId(stored.publicId());
                userRepository.save(user);

                if (oldPublicId != null && !oldPublicId.isBlank()) {
                    afterCommitExecutor.execute(() -> imageStorageService.delete(oldPublicId));
                }
                return stored.url();
            });
        } catch (Exception ex) {
            imageStorageService.delete(stored.publicId());
            throw ex;
        }
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
