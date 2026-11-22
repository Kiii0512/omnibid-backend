package com.omnibid.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnibid.common.ApiResponse;
import com.omnibid.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setStatus(HttpStatus.FORBIDDEN.value());

        ApiResponse<Void> apiResponse = ApiResponse.error(ErrorCode.FORBIDDEN, "Bạn không có quyền thực hiện hành động này");
        objectMapper.writeValue(response.getOutputStream(), apiResponse);
    }
}
