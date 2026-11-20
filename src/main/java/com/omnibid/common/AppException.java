package com.omnibid.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

@Getter
public class AppException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final Object data;

    public AppException(String message) {
        super(message);
        this.code = null;
        this.status = HttpStatus.BAD_REQUEST;
        this.data = null;
    }

    public AppException(String message, HttpStatus status) {
        super(message);
        this.code = null;
        this.status = status;
        this.data = null;
    }

    public AppException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
        this.data = null;
    }

    public AppException(String code, String message, HttpStatus status, Object data) {
        super(message);
        this.code = code;
        this.status = status;
        this.data = data;
    }
}
