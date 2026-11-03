package com.omnibid.common;

public final class MaskingUtils {

    private MaskingUtils() {}

    public static String maskName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "***";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].charAt(0) + "***";
        }
        return parts[0].charAt(0) + "***" + parts[parts.length - 1].charAt(0);
    }
}
