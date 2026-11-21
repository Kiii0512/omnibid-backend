package com.omnibid.dto.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 255, message = "Họ tên không được vượt quá 255 ký tự")
        String fullName,

        @Pattern(regexp = "^$|^[0-9]{9,15}$", message = "Số điện thoại không hợp lệ")
        String phoneNumber,

        @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
        String address
) {}
