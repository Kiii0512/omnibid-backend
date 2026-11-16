package com.omnibid.dto.product;

import java.time.LocalDateTime;

public record ProductImageResponse(
        Long id,
        String imageUrl,
        String publicId,
        Boolean isPrimary,
        LocalDateTime createdAt
) {}

