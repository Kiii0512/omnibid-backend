package com.omnibid.common;

import jakarta.persistence.LockTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleConflict_MapsLockTimeoutExceptionsTo409Conflict() {
        ResponseEntity<ApiResponse<Void>> res1 = handler.handleConflict(new PessimisticLockingFailureException("lock wait timeout"));
        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res1.getBody()).isNotNull();
        assertThat(res1.getBody().getError().code()).isEqualTo(ErrorCode.CONFLICT.name());

        ResponseEntity<ApiResponse<Void>> res2 = handler.handleConflict(new LockTimeoutException("timeout"));
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res2.getBody()).isNotNull();
        assertThat(res2.getBody().getError().code()).isEqualTo(ErrorCode.CONFLICT.name());
    }
}
