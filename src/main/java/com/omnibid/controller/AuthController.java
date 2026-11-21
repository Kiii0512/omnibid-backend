package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.dto.auth.LoginRequest;
import com.omnibid.dto.auth.LoginResponse;
import com.omnibid.dto.auth.RegisterRequest;
import com.omnibid.dto.user.UserProfileResponse;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserProfileResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserProfileResponse response = authService.register(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = extractClientIp(httpRequest);
        LoginResponse response = authService.login(request, clientIp);
        return ApiResponse.success(response);
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, String>> logout(@AuthenticationPrincipal CustomUserDetails userDetails) {
        CurrentUser currentUser = CurrentUser.of(userDetails);
        authService.logout(currentUser.id());
        return ApiResponse.success(Map.of(
                "message", "Đăng xuất thành công, mọi phiên đăng nhập của tài khoản đã kết thúc"
        ));
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
