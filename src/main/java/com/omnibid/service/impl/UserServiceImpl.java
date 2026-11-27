package com.omnibid.service.impl;

import com.omnibid.common.AfterCommitExecutor;
import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.ImageValidator;
import com.omnibid.dto.storage.StoredImage;
import com.omnibid.dto.user.AvatarResponse;
import com.omnibid.dto.user.ChangePasswordRequest;
import com.omnibid.dto.user.UpdateProfileRequest;
import com.omnibid.dto.user.UserProfileResponse;
import com.omnibid.entity.User;
import com.omnibid.repository.UserRepository;
import com.omnibid.service.ImageStorageService;
import com.omnibid.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ImageValidator imageValidator;
    private final ImageStorageService imageStorageService;
    private final AfterCommitExecutor afterCommitExecutor;
    private final TransactionTemplate transactionTemplate;

    @Value("${cloudinary.folder:omnibid}")
    private String cloudinaryFolder = "omnibid";

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

        String newFullName = (request.fullName() != null && !request.fullName().isBlank())
                ? request.fullName().trim()
                : user.getFullName();
        String newPhoneNumber = request.phoneNumber() != null
                ? (request.phoneNumber().isBlank() ? null : request.phoneNumber().trim())
                : user.getPhoneNumber();
        String newAddress = request.address() != null
                ? (request.address().isBlank() ? null : request.address().trim())
                : user.getAddress();

        userRepository.updateProfileFields(userId, newFullName, newPhoneNumber, newAddress);

        return new UserProfileResponse(
                user.getId(),
                newFullName,
                user.getEmail(),
                newPhoneNumber,
                user.getAvatarUrl(),
                newAddress,
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }

    @Override
    public AvatarResponse updateAvatar(Long userId, MultipartFile file) {
        ImageValidator.ValidatedImage validated = imageValidator.validate(file);
        String folder = normalizeBaseFolder() + "/avatars";
        StoredImage uploaded = imageStorageService.upload(validated.bytes(), folder);

        try {
            return transactionTemplate.execute(status -> {
                User user = getUser(userId);
                String oldPublicId = user.getAvatarPublicId();
                userRepository.updateAvatarFields(userId, uploaded.url(), uploaded.publicId());

                if (oldPublicId != null && !oldPublicId.isBlank() && !oldPublicId.equals(uploaded.publicId())) {
                    afterCommitExecutor.runAfterCommit(() -> imageStorageService.delete(oldPublicId));
                }
                return new AvatarResponse(uploaded.url());
            });
        } catch (RuntimeException ex) {
            imageStorageService.delete(uploaded.publicId());
            throw ex;
        }
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getUser(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Mật khẩu hiện tại không chính xác",
                    Map.of("fields", Map.of("currentPassword", "Mật khẩu hiện tại không chính xác"))
            );
        }

        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Mật khẩu xác nhận không khớp",
                    Map.of("fields", Map.of("confirmPassword", "Mật khẩu xác nhận không khớp"))
            );
        }

        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Mật khẩu mới không được trùng với mật khẩu hiện tại",
                    Map.of("fields", Map.of("newPassword", "Mật khẩu mới không được trùng với mật khẩu hiện tại"))
            );
        }

        userRepository.updatePasswordAndIncrementTokenVersion(userId, passwordEncoder.encode(request.newPassword()));
    }

    private String normalizeBaseFolder() {
        if (cloudinaryFolder == null || cloudinaryFolder.isBlank()) {
            return "omnibid";
        }
        return cloudinaryFolder.endsWith("/")
                ? cloudinaryFolder.substring(0, cloudinaryFolder.length() - 1)
                : cloudinaryFolder.trim();
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng"));
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
