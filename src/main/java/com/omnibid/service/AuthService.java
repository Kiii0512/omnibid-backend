package com.omnibid.service;

import com.omnibid.dto.auth.AuthResponse;
import com.omnibid.dto.auth.LoginRequest;
import com.omnibid.dto.auth.RegisterRequest;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}

