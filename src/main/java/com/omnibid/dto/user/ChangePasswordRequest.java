package com.omnibid.dto.user;

import com.omnibid.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(
        @NotBlank(message = "Mật khẩu hiện tại không được để trống")
        String currentPassword,

        @NotBlank(message = "Mật khẩu mới không được để trống")
        @MaxUtf8Bytes(value = 72, message = "Mật khẩu mới không được vượt quá 72 byte UTF-8")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
                message = "Mật khẩu mới phải có ít nhất 8 ký tự, gồm ít nhất 1 chữ hoa, 1 chữ thường và 1 chữ số"
        )
        String newPassword,

        @NotBlank(message = "Xác nhận mật khẩu không được để trống")
        String confirmPassword
) {}
