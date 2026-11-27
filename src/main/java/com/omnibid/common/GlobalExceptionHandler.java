package com.omnibid.common;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        ApiResponse<Void> body = ApiResponse.error(ex.getCode(), ex.getMessage(), ex.getDetails());
        HttpHeaders headers = new HttpHeaders();
        if (ex.getCode() == ErrorCode.TOO_MANY_REQUESTS && ex.getDetails() != null) {
            Object retryAfter = ex.getDetails().get("retryAfterSeconds");
            if (retryAfter != null) {
                headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
            }
        }
        return new ResponseEntity<>(body, headers, ex.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return validationError(ErrorCode.VALIDATION_ERROR.getDefaultMessage(), fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String path = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "parameter";
            int dot = path.lastIndexOf('.');
            String fieldName = dot >= 0 ? path.substring(dot + 1) : path;
            fields.putIfAbsent(fieldName, violation.getMessage());
        }
        return validationError(ErrorCode.VALIDATION_ERROR.getDefaultMessage(), fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            String paramName = result.getMethodParameter().getParameterName();
            String key = paramName != null ? paramName : "parameter";
            result.getResolvableErrors().forEach(err -> {
                String msg = err.getDefaultMessage() != null ? err.getDefaultMessage() : "Giá trị không hợp lệ";
                fields.putIfAbsent(key, msg);
            });
        });
        return validationError(ErrorCode.VALIDATION_ERROR.getDefaultMessage(), fields);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String name = ex.getName();
        Map<String, String> fields = Map.of(name, "Giá trị không đúng định dạng");
        return validationError("Tham số '" + name + "' không đúng định dạng", fields);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        Map<String, String> fields = Map.of(ex.getParameterName(), "Tham số bắt buộc");
        return validationError("Thiếu tham số bắt buộc: " + ex.getParameterName(), fields);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        Map<String, String> fields = Map.of(ex.getHeaderName(), "Header bắt buộc");
        return validationError("Thiếu header bắt buộc: " + ex.getHeaderName(), fields);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException ex) {
        String part = ex.getRequestPartName();
        Map<String, String> fields = Map.of(part, "Phần dữ liệu bắt buộc");
        return validationError("Thiếu phần dữ liệu bắt buộc: " + part, fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("HttpMessageNotReadableException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR, "Dữ liệu yêu cầu không đúng định dạng JSON"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        log.warn("MaxUploadSizeExceededException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(ErrorCode.PAYLOAD_TOO_LARGE));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipartException(MultipartException ex) {
        log.warn("MultipartException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR, "Không thể xử lý yêu cầu multipart"));
    }

    @ExceptionHandler({
            DataIntegrityViolationException.class,
            CannotAcquireLockException.class,
            PessimisticLockingFailureException.class,
            ObjectOptimisticLockingFailureException.class,
            LockTimeoutException.class,
            PessimisticLockException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleConflict(Exception ex) {
        log.warn("Database conflict ({}): {}", ex.getClass().getSimpleName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ErrorCode.CONFLICT));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ErrorCode.UNAUTHORIZED));
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(Exception ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCode.FORBIDDEN));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(ErrorCode.NOT_FOUND, "Phương thức HTTP không được hỗ trợ cho đường dẫn này"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled internal server error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR));
    }

    private ResponseEntity<ApiResponse<Void>> validationError(String message, Map<String, String> fields) {
        Map<String, Object> details = fields.isEmpty() ? null : Map.of("fields", fields);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR, message, details));
    }
}
