package com.omnibid.common;

public final class SearchKeywordUtils {

    private SearchKeywordUtils() {
    }

    /**
     * Trims and escapes SQL LIKE wildcard characters ('\\', '%', '_') for use with ESCAPE '\\'.
     * Returns null if input is null or blank.
     */
    public static String escapeLike(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
