package com.omnibid.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "Tên danh mục không được để trống")
        @Size(max = 100, message = "Tên danh mục tối đa 100 ký tự")
        String name,

        @Size(max = 100, message = "Slug tối đa 100 ký tự")
        @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug chỉ gồm chữ thường, chữ số và dấu gạch ngang")
        String slug,

        @Size(max = 50, message = "Icon tối đa 50 ký tự")
        @Pattern(regexp = "^[a-z0-9-]+$", message = "Icon chỉ gồm chữ thường, chữ số và dấu gạch ngang")
        String icon
) {
}
