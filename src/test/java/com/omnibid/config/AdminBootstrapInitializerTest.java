package com.omnibid.config;

import com.omnibid.entity.User;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapInitializerTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void rotatesSeededAdminPasswordWhenInitialPasswordProvided() {
        User admin = User.builder()
                .id(1L)
                .email("admin@omnibid.local")
                .passwordHash(passwordEncoder.encode("Admin@123456"))
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .tokenVersion(0)
                .build();
        when(userRepository.findByEmail("admin@omnibid.local")).thenReturn(Optional.of(admin));

        AdminBootstrapInitializer initializer = new AdminBootstrapInitializer(userRepository, passwordEncoder);
        ReflectionTestUtils.setField(initializer, "initialAdminPassword", "StrongProdPass#99");

        initializer.run(null);

        assertThat(passwordEncoder.matches("StrongProdPass#99", admin.getPasswordHash())).isTrue();
        assertThat(admin.getTokenVersion()).isEqualTo(1);
        verify(userRepository).save(admin);
    }

    @Test
    void doesNotOverwriteAlreadyChangedAdminPassword() {
        String customHash = passwordEncoder.encode("AlreadyChanged#123");
        User admin = User.builder()
                .id(1L)
                .email("admin@omnibid.local")
                .passwordHash(customHash)
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .tokenVersion(2)
                .build();
        when(userRepository.findByEmail("admin@omnibid.local")).thenReturn(Optional.of(admin));

        AdminBootstrapInitializer initializer = new AdminBootstrapInitializer(userRepository, passwordEncoder);
        ReflectionTestUtils.setField(initializer, "initialAdminPassword", "AnotherPass#456");

        initializer.run(null);

        assertThat(admin.getPasswordHash()).isEqualTo(customHash);
        assertThat(admin.getTokenVersion()).isEqualTo(2);
        verify(userRepository, never()).save(any());
    }
}
