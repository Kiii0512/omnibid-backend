package com.omnibid.security;

import lombok.Getter;

@Getter
public class JwtValidationException extends RuntimeException {

    public enum Reason {
        EXPIRED,
        INVALID_SIGNATURE,
        MALFORMED,
        UNSUPPORTED,
        INVALID_CLAIMS
    }

    private final Reason reason;

    public JwtValidationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public JwtValidationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }
}
