package com.omnibid.dto.category;

import java.time.LocalDateTime;

public record CategoryResponse(
        Long id,
        String name,
        String slug,
        String iconUrl,
        LocalDateTime createdAt
) {}
