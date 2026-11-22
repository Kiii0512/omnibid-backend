package com.omnibid.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ProductCondition {
    NEW,
    LIKE_NEW,
    GOOD,
    FAIR,
    POOR;

    @JsonCreator
    public static ProductCondition fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        for (ProductCondition condition : values()) {
            if (condition.name().equals(normalized)) {
                return condition;
            }
        }
        throw new IllegalArgumentException("Unknown product condition: " + value);
    }
}
