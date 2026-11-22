package com.omnibid.common;

public final class MaskingUtils {

    private MaskingUtils() {}

    public static String maskName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "***";
        }
        String trimmed = fullName.trim();
        if (trimmed.length() == 1) {
            return trimmed.charAt(0) + "***";
        }
        return trimmed.charAt(0) + "***" + trimmed.charAt(trimmed.length() - 1);
    }
}
