package com.omnibid.service;

import com.omnibid.common.AfterCommitExecutor;
import com.omnibid.common.ImageValidator;
import com.omnibid.dto.storage.StoredImage;
import com.omnibid.dto.user.AvatarResponse;
import com.omnibid.dto.user.ChangePasswordRequest;
import com.omnibid.dto.user.UpdateProfileRequest;
import com.omnibid.dto.user.UserProfileResponse;
import com.omnibid.entity.User;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.repository.UserRepository;
import com.omnibid.service.impl.UserServiceImpl;
import com.omnibid.test.DirectTransactionTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ImageValidator imageValidator;
    @Mock
    private ImageStorageService imageStorageService;
    @Mock
    private AfterCommitExecutor afterCommitExecutor;

    private UserServiceImpl userService;
    private User sampleUser;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(
                userRepository,
                passwordEncoder,
                imageValidator,
                imageStorageService,
                afterCommitExecutor,
                new DirectTransactionTemplate()
        );
        sampleUser = User.builder()
                .id(1L)
                .fullName("Old Name")
                .email("user@example.com")
                .passwordHash("encodedOld")
                .phoneNumber("0901234567")
                .address("Old Addr")
                .avatarUrl("https://cdn.example/old.jpg")
                .avatarPublicId("omnibid/avatars/old")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .tokenVersion(3)
                .build();
    }

    @Test
    void updateProfile_UsesTargetedColumnUpdateWithoutTouchingTokenVersionOrStatus() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        UserProfileResponse response = userService.updateProfile(
                1L,
                new UpdateProfileRequest("New Name", "0912345678", "New Addr")
        );

        assertThat(response.fullName()).isEqualTo("New Name");
        assertThat(response.phoneNumber()).isEqualTo("0912345678");
        assertThat(response.address()).isEqualTo("New Addr");
        verify(userRepository).updateProfileFields(1L, "New Name", "0912345678", "New Addr");
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateAvatar_UsesTargetedColumnUpdateWithoutTouchingTokenVersionOrStatus() {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(imageValidator.validate(file)).thenReturn(new ImageValidator.ValidatedImage(new byte[]{1, 2, 3}, "jpeg"));
        when(imageStorageService.upload(any(), any())).thenReturn(new StoredImage("https://cdn.example/new.jpg", "omnibid/avatars/new"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        AvatarResponse response = userService.updateAvatar(1L, file);

        assertThat(response.avatarUrl()).isEqualTo("https://cdn.example/new.jpg");
        verify(userRepository).updateAvatarFields(1L, "https://cdn.example/new.jpg", "omnibid/avatars/new");
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_UsesAtomicPasswordAndTokenVersionUpdate() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("OldPass123", "encodedOld")).thenReturn(true);
        when(passwordEncoder.encode("NewPass456")).thenReturn("encodedNew");

        userService.changePassword(1L, new ChangePasswordRequest("OldPass123", "NewPass456", "NewPass456"));

        verify(userRepository).updatePasswordAndIncrementTokenVersion(1L, "encodedNew");
        verify(userRepository, never()).save(any());
    }
}
