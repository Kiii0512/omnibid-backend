package com.omnibid.common;

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
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        ApiResponse<Void> response = ApiResponse.error(
                ex.getErrorCode().name(),
                ex.getMessage(),
                ex.getDetails()
        );
        HttpHeaders headers = new HttpHeaders();
        if (ex.getStatus() == HttpStatus.TOO_MANY_REQUESTS && ex.getDetails() != null) {
            Object retryAfter = ex.getDetails().get("retryAfterSeconds");
            if (retryAfter != null) {
                headers.add("Retry-After", String.valueOf(retryAfter));
            }
        }
        return new ResponseEntity<>(response, headers, ex.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Không hợp lệ",
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));

        String firstErrorMessage = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Dữ liệu đầu vào không hợp lệ");

        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.VALIDATION_ERROR.name(),
                firstErrorMessage,
                Map.of("fields", fields)
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            fields.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        String firstErrorMessage = ex.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse("Dữ liệu đầu vào không hợp lệ");

        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.VALIDATION_ERROR.name(),
                firstErrorMessage,
                Map.of("fields", fields)
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidationException(HandlerMethodValidationException ex) {
        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.VALIDATION_ERROR.name(),
                "Dữ liệu đầu vào không hợp lệ",
                null
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        String message = "Tham số '" + ex.getName() + "' không hợp lệ";
        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.VALIDATION_ERROR.name(),
                message,
                Map.of("parameter", ex.getName())
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        String message = "Thiếu tham số bắt buộc: " + ex.getParameterName();
        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.VALIDATION_ERROR.name(),
                message,
                Map.of("parameter", ex.getParameterName())
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestHeaderException(MissingRequestHeaderException ex) {
        String message = "Thiếu header bắt buộc: " + ex.getHeaderName();
        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.VALIDATION_ERROR.name(),
                message,
                Map.of("header", ex.getHeaderName())
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("HttpMessageNotReadableException: {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.VALIDATION_ERROR.name(),
                "Request body is malformed or invalid",
                null
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestPartException(MissingServletRequestPartException ex) {
        log.warn("Missing multipart request part: {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.VALIDATION_ERROR.name(),
                "Phần yêu cầu '" + ex.getRequestPartName() + "' là bắt buộc",
                null
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        log.warn("Max upload size exceeded: {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.PAYLOAD_TOO_LARGE.name(),
                ErrorCode.PAYLOAD_TOO_LARGE.getDefaultMessage(),
                null
        );
        return new ResponseEntity<>(response, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipartException(MultipartException ex) {
        log.warn("Multipart processing failed: {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.INVALID_IMAGE.name(),
                "Hình ảnh không hợp lệ hoặc lỗi xử lý multipart",
                null
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({
            DataIntegrityViolationException.class,
            CannotAcquireLockException.class,
            PessimisticLockingFailureException.class,
            ObjectOptimisticLockingFailureException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleConflictExceptions(Exception ex) {
        log.warn("Conflict exception: {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.CONFLICT.name(),
                ErrorCode.CONFLICT.getDefaultMessage(),
                null
        );
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.UNAUTHORIZED.name(),
                "Sai email hoặc mật khẩu khi đăng nhập",
                null
        );
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(Exception ex) {
        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.FORBIDDEN.name(),
                "Bạn không có quyền thực hiện hành động này",
                null
        );
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException ex) {
        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.NOT_FOUND.name(),
                ErrorCode.NOT_FOUND.getDefaultMessage(),
                null
        );
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.NOT_FOUND.name(),
                "Phương thức HTTP không được hỗ trợ",
                null
        );
        return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        log.error("Unhandled internal server error: ", ex);
        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.INTERNAL_ERROR.name(),
                ErrorCode.INTERNAL_ERROR.getDefaultMessage(),
                null
        );
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
