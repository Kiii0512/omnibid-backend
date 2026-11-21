package com.omnibid.service;

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
import com.omnibid.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private LoginAttemptLimiter loginAttemptLimiter;

    @InjectMocks
    private AuthServiceImpl authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .fullName("John Doe")
                .email("john@example.com")
                .passwordHash("encodedPassword")
                .phoneNumber("0123456789")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .tokenVersion(0)
                .build();
    }

    @Test
    void register_Success() {
        RegisterRequest request = RegisterRequest.builder()
                .fullName("John Doe")
                .email("john@example.com")
                .password("Password123")
                .phoneNumber("0123456789")
                .build();

        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        UserProfileResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("john@example.com", response.email());
        assertEquals("John Doe", response.fullName());
        assertEquals(UserRole.USER, response.role());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_DuplicateEmail_ThrowsBusinessException() {
        RegisterRequest request = RegisterRequest.builder()
                .fullName("John Doe")
                .email("john@example.com")
                .password("Password123")
                .build();

        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(request));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    void login_Success() {
        LoginRequest request = LoginRequest.builder()
                .email("john@example.com")
                .password("Password123")
                .build();

        when(loginAttemptLimiter.isBlocked("john@example.com", "127.0.0.1")).thenReturn(false);
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("Password123", "encodedPassword")).thenReturn(true);
        when(jwtTokenProvider.generateTokenWithExpiry(1L, UserRole.USER, 0))
                .thenReturn(new JwtTokenProvider.TokenResult("mockJwtToken", Instant.now().plusSeconds(86400)));

        LoginResponse response = authService.login(request, "127.0.0.1");

        assertNotNull(response);
        assertEquals("mockJwtToken", response.accessToken());
        assertEquals(UserRole.USER, response.role());
        assertEquals(1L, response.user().id());
        verify(loginAttemptLimiter).reset("john@example.com", "127.0.0.1");
    }

    @Test
    void login_WrongPassword_ThrowsBusinessException() {
        LoginRequest request = LoginRequest.builder()
                .email("john@example.com")
                .password("wrongPassword")
                .build();

        when(loginAttemptLimiter.isBlocked("john@example.com", "127.0.0.1")).thenReturn(false);
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrongPassword", "encodedPassword")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request, "127.0.0.1"));
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
        verify(loginAttemptLimiter).recordFailedAttempt("john@example.com", "127.0.0.1");
    }

    @Test
    void login_BannedUser_ThrowsBusinessException() {
        sampleUser.setStatus(UserStatus.BANNED);

        LoginRequest request = LoginRequest.builder()
                .email("john@example.com")
                .password("Password123")
                .build();

        when(loginAttemptLimiter.isBlocked("john@example.com", "127.0.0.1")).thenReturn(false);
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("Password123", "encodedPassword")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request, "127.0.0.1"));
        assertEquals(ErrorCode.ACCOUNT_BANNED, ex.getErrorCode());
    }

    @Test
    void logout_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        authService.logout(1L);

        assertEquals(1, sampleUser.getTokenVersion());
        verify(userRepository).save(sampleUser);
    }
}
