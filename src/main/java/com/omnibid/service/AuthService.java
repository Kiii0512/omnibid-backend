package com.omnibid.service;

import com.omnibid.dto.auth.LoginRequest;
import com.omnibid.dto.auth.LoginResponse;
import com.omnibid.dto.auth.RegisterRequest;
import com.omnibid.dto.user.UserProfileResponse;

public interface AuthService {
    UserProfileResponse register(RegisterRequest request);
    LoginResponse login(LoginRequest request, String clientIp);
    void logout(Long userId);
}
