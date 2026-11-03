package com.omnibid.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Object>> handleAppException(AppException ex) {
        String code = ex.getCode();
        if (code == null) {
            code = ex.getStatus().name();
        }
        ApiResponse<Object> response = ApiResponse.error(code, ex.getMessage(), ex.getData());
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        if (ex.getStatus() == HttpStatus.TOO_MANY_REQUESTS && ex.getData() instanceof Map<?, ?> map) {
            Object retryAfter = map.get("retryAfterSeconds");
            if (retryAfter != null) {
                headers.add("Retry-After", String.valueOf(retryAfter));
            }
        }
        return new ResponseEntity<>(response, headers, ex.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage,
                        (existing, replacement) -> existing
                ));

        String firstErrorMessage = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Validation failed");

        ApiResponse<Map<String, String>> response = ApiResponse.<Map<String, String>>builder()
                .success(false)
                .errorCode("VALIDATION_ERROR")
                .message(firstErrorMessage)
                .data(errors)
                .build();
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentialsException(BadCredentialsException ex) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .errorCode("UNAUTHORIZED")
                .message("Invalid email or password")
                .build();
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .errorCode("FORBIDDEN")
                .message("Access denied: " + ex.getMessage())
                .build();
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        log.error("Unhandled internal server error: ", ex);
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .errorCode("INTERNAL_ERROR")
                .message("An internal server error occurred")
                .build();
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
