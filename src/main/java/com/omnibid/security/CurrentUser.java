package com.omnibid.security;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import org.springframework.http.HttpStatus;

public record CurrentUser(Long id, boolean isAdmin) {

    public static CurrentUser of(CustomUserDetails principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User not authenticated");
        }
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return new CurrentUser(principal.getId(), isAdmin);
    }
}
