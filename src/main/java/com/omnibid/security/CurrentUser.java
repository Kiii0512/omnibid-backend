package com.omnibid.security;

import com.omnibid.common.AppException;
import org.springframework.http.HttpStatus;

public record CurrentUser(Long id, boolean isAdmin) {

    public static CurrentUser of(CustomUserDetails principal) {
        if (principal == null) {
            throw new AppException("UNAUTHORIZED", "User not authenticated", HttpStatus.UNAUTHORIZED);
        }
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return new CurrentUser(principal.getId(), isAdmin);
    }
}
