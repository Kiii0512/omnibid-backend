package com.omnibid.service.impl;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.dto.auth.LoginRequest;
import com.omnibid.dto.auth.LoginResponse;
import com.omnibid.dto.auth.RegisterRequest;
import com.omnibid.dto.user.UserProfileResponse;
import com.omnibid.dto.user.UserSummary;
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

import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Email hoặc mật khẩu không đúng";
    // Pre-computed BCrypt (cost 10) hash used when email does not exist to equalize login response timing (LOW-01)
    private static final String DUMMY_PASSWORD_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginAttemptLimiter loginAttemptLimiter;

    @Override
    @Transactional
    public UserProfileResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Email đã được đăng ký",
                    Map.of("fields", Map.of("email", "Email đã được đăng ký"))
            );
        }

        String phoneNumber = request.getPhoneNumber();
        if (phoneNumber != null) {
            phoneNumber = phoneNumber.isBlank() ? null : phoneNumber.trim();
        }

        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(phoneNumber)
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
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);

        loginAttemptLimiter.checkAllowed(normalizedEmail, clientIp);

        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        String candidateHash = user != null ? user.getPasswordHash() : DUMMY_PASSWORD_HASH;
        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), candidateHash);
        if (user == null || !passwordMatches) {
            loginAttemptLimiter.recordFailure(normalizedEmail, clientIp);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE);
        }

        if (user.isBanned()) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
        }

        loginAttemptLimiter.recordSuccess(normalizedEmail, clientIp);

        JwtTokenProvider.IssuedToken issuedToken = jwtTokenProvider.issueToken(
                user.getId(),
                user.getRole(),
                user.getTokenVersion()
        );

        UserSummary summary = new UserSummary(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getAvatarUrl()
        );

        return new LoginResponse(
                issuedToken.accessToken(),
                "Bearer",
                issuedToken.expiresInSeconds(),
                issuedToken.expiresAt(),
                user.getRole(),
                summary
        );
    }

    @Override
    @Transactional
    public void logout(Long userId) {
        int updated = userRepository.incrementTokenVersion(userId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng");
        }
    }
}
