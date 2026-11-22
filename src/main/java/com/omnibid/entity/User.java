package com.omnibid.entity;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "avatar_public_id", length = 255)
    private String avatarPublicId;

    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private int tokenVersion = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public void ban() {
        if (this.role == UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.CANNOT_BAN_ADMIN, "Cannot ban an admin account");
        }
        if (this.status != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Cannot ban user when status is " + this.status);
        }
        this.status = UserStatus.BANNED;
    }

    public void unban() {
        if (this.status != UserStatus.BANNED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Cannot unban user when status is " + this.status);
        }
        this.status = UserStatus.ACTIVE;
    }

    public void increaseTokenVersion() {
        this.tokenVersion++;
    }

    public boolean isBanned() {
        return this.status == UserStatus.BANNED;
    }
}
