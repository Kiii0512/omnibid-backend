package com.omnibid.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        String errorCode;
        String message;

        if (Boolean.TRUE.equals(request.getAttribute("ACCOUNT_BANNED"))) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            errorCode = "ACCOUNT_BANNED";
            message = "Tài khoản của bạn đã bị khóa";
        } else {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            errorCode = "UNAUTHORIZED";
            message = "Yêu cầu chưa xác thực hoặc token không hợp lệ";
        }

        String json = String.format(
                "{\"success\":false,\"errorCode\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                errorCode, message, LocalDateTime.now()
        );
        response.getWriter().write(json);
    }
}
