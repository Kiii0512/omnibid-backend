package com.omnibid.common;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;
import java.util.Set;

public final class SortWhitelist {

    private SortWhitelist() {
    }

    /**
     * Kiểm tra mọi thuộc tính sắp xếp trong {@link Pageable} đều nằm trong danh sách cho phép.
     */
    public static void validate(Pageable pageable, Set<String> allowedProperties) {
        if (pageable == null || pageable.getSort().isUnsorted()) {
            return;
        }
        for (Sort.Order order : pageable.getSort()) {
            if (!allowedProperties.contains(order.getProperty())) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Tham số sắp xếp không hợp lệ: " + order.getProperty(),
                        Map.of("fields", Map.of("sort", "Không hỗ trợ sắp xếp theo trường '" + order.getProperty() + "'"))
                );
            }
        }
    }

    /**
     * Kiểm tra cặp "property,direction" (viết thường chiều, ví dụ "endTime,asc") khớp chính xác danh sách cho phép.
     */
    public static void validateDirections(Pageable pageable, Set<String> allowedPropertyDirections) {
        if (pageable == null || pageable.getSort().isUnsorted()) {
            return;
        }
        for (Sort.Order order : pageable.getSort()) {
            String key = order.getProperty() + "," + order.getDirection().name().toLowerCase();
            if (!allowedPropertyDirections.contains(key)) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Tham số sắp xếp không hợp lệ: " + key,
                        Map.of("fields", Map.of("sort", "Không hỗ trợ sắp xếp '" + key + "'"))
                );
            }
        }
    }
}
