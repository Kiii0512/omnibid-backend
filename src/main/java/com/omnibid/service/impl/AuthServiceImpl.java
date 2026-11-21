package com.omnibid.service.impl;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.dto.auth.LoginRequest;
import com.omnibid.dto.auth.LoginResponse;
import com.omnibid.dto.auth.RegisterRequest;
import com.omnibid.dto.user.UserProfileResponse;
import com.omnibid.entity.User;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.repository.UserRepository;
import com.omnibid.security.JwtTokenProvider;
import com.omnibid.security.LoginAttemptLimiter;
import com.omnibid.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginAttemptLimiter loginAttemptLimiter;

    @Override
    @Transactional
    public UserProfileResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Email đã tồn tại",
                    Map.of("fields", Map.of("email", "Email đã tồn tại"))
            );
        }

        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .tokenVersion(0)
                .build();

        User savedUser = userRepository.save(user);

        return new UserProfileResponse(
                savedUser.getId(),
                savedUser.getFullName(),
                savedUser.getEmail(),
                savedUser.getPhoneNumber(),
                savedUser.getAvatarUrl(),
                savedUser.getAddress(),
                savedUser.getRole(),
                savedUser.getStatus(),
                savedUser.getCreatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request, String clientIp) {
        String normalizedEmail = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";

        if (loginAttemptLimiter.isBlocked(normalizedEmail, clientIp)) {
            throw new BusinessException(
                    ErrorCode.TOO_MANY_REQUESTS,
                    "Tài khoản bị tạm khóa đăng nhập 15 phút do nhập sai quá 5 lần"
            );
        }

        User user = userRepository.findByEmail(normalizedEmail).orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            loginAttemptLimiter.recordFailedAttempt(normalizedEmail, clientIp);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Sai email hoặc mật khẩu");
        }

        if (user.getStatus() == UserStatus.BANNED) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED, "Tài khoản của bạn đã bị khóa");
        }

        loginAttemptLimiter.reset(normalizedEmail, clientIp);

        int tokenVersion = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
        JwtTokenProvider.TokenResult tokenResult =
                jwtTokenProvider.generateTokenWithExpiry(user.getId(), user.getRole(), tokenVersion);

        LoginResponse.UserSummary summary = new LoginResponse.UserSummary(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getAvatarUrl()
        );

        return new LoginResponse(
                tokenResult.token(),
                "Bearer",
                86400L,
                tokenResult.expiresAt(),
                user.getRole(),
                summary
        );
    }

    @Override
    @Transactional
    public void logout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found with id: " + userId));
        user.increaseTokenVersion();
        userRepository.save(user);
    }
}
