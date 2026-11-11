package com.omnibid.dto.product;

import com.omnibid.enums.ProductCondition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class ProductCreateRequest {

    @NotNull(message = "Category ID cannot be null")
    private Long categoryId;

    @NotBlank(message = "Product title cannot be blank")
    @Size(max = 255, message = "Product title must not exceed 255 characters")
    private String title;

    @NotBlank(message = "Product description cannot be blank")
    private String description;

    @NotNull(message = "Product condition cannot be null")
    private ProductCondition condition;
}

