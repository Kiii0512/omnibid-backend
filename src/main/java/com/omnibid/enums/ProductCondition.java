package com.omnibid.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ProductCondition {
    NEW,
    LIKE_NEW,
    GOOD,
    FAIR,
    POOR;

    @JsonCreator
    public static ProductCondition fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return ProductCondition.valueOf(value.trim().toUpperCase());
    }
}
