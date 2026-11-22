package com.omnibid.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnibid.common.ApiResponse;
import com.omnibid.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ErrorCode code;
        String message;
        HttpStatus status;

        if (Boolean.TRUE.equals(request.getAttribute("ACCOUNT_BANNED"))) {
            status = HttpStatus.FORBIDDEN;
            code = ErrorCode.ACCOUNT_BANNED;
            message = "Tài khoản của bạn đã bị khóa";
        } else {
            status = HttpStatus.UNAUTHORIZED;
            code = ErrorCode.UNAUTHORIZED;
            message = "Yêu cầu chưa được xác thực hoặc thông tin xác thực không hợp lệ";
        }

        response.setStatus(status.value());
        ApiResponse<Void> apiResponse = ApiResponse.error(code, message);
        objectMapper.writeValue(response.getOutputStream(), apiResponse);
    }
}
