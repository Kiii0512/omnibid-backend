package com.omnibid.dto.admin;

import jakarta.validation.constraints.Size;

public record AdminReasonRequest(
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason
) {}
