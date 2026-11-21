package com.omnibid.common;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;
import java.util.Set;

public final class SortWhitelist {

    private SortWhitelist() {}

    public static void validate(Pageable pageable, Set<String> allowedProperties) {
        if (pageable == null || pageable.getSort().isUnsorted()) {
            return;
        }
        for (Sort.Order order : pageable.getSort()) {
            if (!allowedProperties.contains(order.getProperty())) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Trường sắp xếp không hợp lệ: " + order.getProperty(),
                        Map.of("allowedSorts", allowedProperties, "invalidSort", order.getProperty())
                );
            }
        }
    }
}
