package com.omnibid.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "Tên danh mục không được để trống")
        @Size(max = 100, message = "Tên danh mục không được vượt quá 100 ký tự")
        String name,

        String slug,

        @Size(max = 50, message = "Icon không được vượt quá 50 ký tự")
        @Pattern(regexp = "^$|^[a-z0-9-]+$", message = "Tên icon phải là chuỗi kebab-case")
        String icon
) {}
