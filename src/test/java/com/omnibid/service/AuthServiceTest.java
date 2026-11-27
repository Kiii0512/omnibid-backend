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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Map;
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
                .createdAt(Instant.parse("2026-11-22T02:00:00Z"))
                .build();
    }

    @Test
    void register_Success_ReturnsProfileWithoutToken() {
        RegisterRequest request = RegisterRequest.builder()
                .fullName("John Doe")
                .email("John@Example.com")
                .password("Password1")
                .phoneNumber("0123456789")
                .build();

        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        UserProfileResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals("john@example.com", response.email());
        assertEquals(UserRole.USER, response.role());
        verify(jwtTokenProvider, never()).issueToken(any(), any(), anyInt());
    }

    @Test
    @DisplayName("Đăng ký trùng email trả 400 VALIDATION_ERROR với details.fields.email")
    void register_DuplicateEmail_ThrowsValidationError() {
        RegisterRequest request = RegisterRequest.builder()
                .fullName("John Doe")
                .email("john@example.com")
                .password("Password1")
                .build();

        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(request));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getCode());
        assertNotNull(ex.getDetails());
        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) ex.getDetails().get("fields");
        assertTrue(fields.containsKey("email"));
    }

    @Test
    void login_Success() {
        LoginRequest request = LoginRequest.builder()
                .email("john@example.com")
                .password("Password1")
                .build();
        Instant expiresAt = Instant.parse("2026-11-23T02:00:00Z");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("Password1", "encodedPassword")).thenReturn(true);
        when(jwtTokenProvider.issueToken(1L, UserRole.USER, 0))
                .thenReturn(new JwtTokenProvider.IssuedToken("mockJwtToken", 86400L, expiresAt));

        LoginResponse response = authService.login(request, "127.0.0.1");

        assertNotNull(response);
        assertEquals("mockJwtToken", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(86400L, response.expiresIn());
        assertEquals(expiresAt, response.expiresAt());
        assertEquals(UserRole.USER, response.role());
        assertEquals(1L, response.user().id());
        verify(loginAttemptLimiter).checkAllowed("john@example.com", "127.0.0.1");
        verify(loginAttemptLimiter).recordSuccess("john@example.com", "127.0.0.1");
    }

    @Test
    void login_WrongPassword_RecordsFailureAndThrowsUnauthorized() {
        LoginRequest request = LoginRequest.builder()
                .email("john@example.com")
                .password("wrongPassword")
                .build();

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrongPassword", "encodedPassword")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request, "127.0.0.1"));
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getCode());
        verify(loginAttemptLimiter).recordFailure("john@example.com", "127.0.0.1");
    }

    @Test
    void login_NonExistentEmail_StillRunsPasswordMatchesAndThrowsUnauthorized() {
        LoginRequest request = LoginRequest.builder()
                .email("unknown@example.com")
                .password("SomePass123")
                .build();

        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(eq("SomePass123"), anyString())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request, "127.0.0.1"));
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getCode());
        verify(passwordEncoder).matches(eq("SomePass123"), anyString());
        verify(loginAttemptLimiter).recordFailure("unknown@example.com", "127.0.0.1");
    }

    @Test
    void login_BannedUser_ThrowsAccountBanned() {
        sampleUser.setStatus(UserStatus.BANNED);

        LoginRequest request = LoginRequest.builder()
                .email("john@example.com")
                .password("Password1")
                .build();

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("Password1", "encodedPassword")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request, "127.0.0.1"));
        assertEquals(ErrorCode.ACCOUNT_BANNED, ex.getCode());
        verify(loginAttemptLimiter, never()).recordFailure(any(), any());
    }

    @Test
    void logout_IncreasesTokenVersionAtomically() {
        when(userRepository.incrementTokenVersion(1L)).thenReturn(1);

        authService.logout(1L);

        verify(userRepository).incrementTokenVersion(1L);
        verify(userRepository, never()).save(any());
    }

    @Test
    void logout_UserNotFound_ThrowsNotFound() {
        when(userRepository.incrementTokenVersion(999L)).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.logout(999L));
        assertEquals(ErrorCode.NOT_FOUND, ex.getCode());
    }
}
