package com.omnibid.common;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Temporary test-only adapter for Phase 1-8 while tests are gradually migrated to v4.
 */
public class AppException extends BusinessException {

    public AppException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
    }

    public AppException(String message, HttpStatus status) {
        super(mapStatus(status), message);
    }

    public AppException(String code, String message, HttpStatus status) {
        super(mapCode(code, status), message);
    }

    @SuppressWarnings("unchecked")
    public AppException(String code, String message, HttpStatus status, Object data) {
        super(mapCode(code, status), message, data instanceof Map ? (Map<String, Object>) data : null);
    }

    private static ErrorCode mapStatus(HttpStatus status) {
        if (status == HttpStatus.NOT_FOUND) return ErrorCode.NOT_FOUND;
        if (status == HttpStatus.FORBIDDEN) return ErrorCode.FORBIDDEN;
        if (status == HttpStatus.UNAUTHORIZED) return ErrorCode.UNAUTHORIZED;
        if (status == HttpStatus.CONFLICT) return ErrorCode.CONFLICT;
        if (status == HttpStatus.PAYLOAD_TOO_LARGE) return ErrorCode.PAYLOAD_TOO_LARGE;
        if (status == HttpStatus.TOO_MANY_REQUESTS) return ErrorCode.TOO_MANY_REQUESTS;
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) return ErrorCode.INTERNAL_ERROR;
        return ErrorCode.VALIDATION_ERROR;
    }

    private static ErrorCode mapCode(String code, HttpStatus status) {
        if (code == null) return mapStatus(status);
        try {
            return ErrorCode.valueOf(code);
        } catch (IllegalArgumentException e) {
            if ("EMAIL_ALREADY_EXISTS".equals(code) || "SLUG_ALREADY_EXISTS".equals(code) || "INVALID_PASSWORD".equals(code)) {
                return ErrorCode.VALIDATION_ERROR;
            }
            if ("INVALID_CREDENTIALS".equals(code)) {
                return ErrorCode.UNAUTHORIZED;
            }
            if (code.endsWith("_NOT_FOUND")) {
                return ErrorCode.NOT_FOUND;
            }
            return mapStatus(status);
        }
    }
}
