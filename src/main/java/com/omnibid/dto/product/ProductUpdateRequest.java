package com.omnibid.dto.product;

import com.omnibid.enums.ProductCondition;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductUpdateRequest {

    private Long categoryId;

    @Size(min = 1, max = 255, message = "Product title must be between 1 and 255 characters")
    private String title;

    @Size(min = 1, message = "Product description cannot be empty")
    private String description;

    private ProductCondition condition;
}

