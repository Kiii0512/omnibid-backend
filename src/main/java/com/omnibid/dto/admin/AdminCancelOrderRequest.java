package com.omnibid.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCancelOrderRequest(
        @NotBlank(message = "Lý do hủy đơn không được để trống")
        @Size(max = 500, message = "Lý do hủy tối đa 500 ký tự")
        String reason
) {}
