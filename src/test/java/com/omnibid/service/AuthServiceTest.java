package com.omnibid.service;

import com.omnibid.common.AppException;
import com.omnibid.dto.auth.AuthResponse;
import com.omnibid.dto.auth.LoginRequest;
import com.omnibid.dto.auth.RegisterRequest;
import com.omnibid.entity.User;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.repository.UserRepository;
import com.omnibid.security.JwtTokenProvider;
import com.omnibid.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

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
                .address("123 Street")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void register_Success() {
        RegisterRequest request = RegisterRequest.builder()
                .fullName("John Doe")
                .email("john@example.com")
                .password("rawPassword")
                .phoneNumber("0123456789")
                .address("123 Street")
                .build();

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(jwtTokenProvider.generateToken(sampleUser.getEmail(), sampleUser.getRole().name())).thenReturn("mockToken");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("mockToken", response.getAccessToken());
        assertEquals(sampleUser.getEmail(), response.getEmail());
        assertEquals("USER", response.getRole());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void register_DuplicateEmail_ThrowsAppException() {
        RegisterRequest request = RegisterRequest.builder()
                .fullName("John Doe")
                .email("john@example.com")
                .password("rawPassword")
                .build();

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> authService.register(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Email is already registered", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_Success() {
        LoginRequest request = LoginRequest.builder()
                .email("john@example.com")
                .password("rawPassword")
                .build();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("rawPassword", "encodedPassword")).thenReturn(true);
        when(jwtTokenProvider.generateToken(sampleUser.getEmail(), sampleUser.getRole().name())).thenReturn("mockToken");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mockToken", response.getAccessToken());
        assertEquals(sampleUser.getEmail(), response.getEmail());
    }

    @Test
    void login_WrongPassword_ThrowsAppException() {
        LoginRequest request = LoginRequest.builder()
                .email("john@example.com")
                .password("wrongPassword")
                .build();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrongPassword", "encodedPassword")).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> authService.login(request));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void login_BannedUser_ThrowsAppException() {
        sampleUser.setStatus(UserStatus.BANNED);
        LoginRequest request = LoginRequest.builder()
                .email("john@example.com")
                .password("rawPassword")
                .build();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("rawPassword", "encodedPassword")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> authService.login(request));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("User account is banned", ex.getMessage());
    }
}

