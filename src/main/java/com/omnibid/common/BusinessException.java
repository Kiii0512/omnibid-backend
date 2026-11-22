package com.omnibid.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> details;

    public BusinessException(ErrorCode code) {
        this(code, code.getDefaultMessage(), null);
    }

    public BusinessException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public BusinessException(ErrorCode code, String message, Map<String, Object> details) {
        super(message != null && !message.isBlank() ? message : code.getDefaultMessage());
        this.code = code;
        this.details = (details != null && !details.isEmpty()) ? details : null;
    }

    public HttpStatus getStatus() {
        return code.getStatus();
    }
}
