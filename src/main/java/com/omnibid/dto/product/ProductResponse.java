package com.omnibid.dto.product;

import com.omnibid.enums.ProductCondition;
import com.omnibid.enums.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private Long id;

    private Long sellerId;

    private String sellerName;

    private Long categoryId;

    private String categoryName;

    private String title;

    private String description;

    private ProductCondition condition;

    private ProductStatus status;

    private List<ProductImageResponse> images;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

