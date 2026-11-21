package com.omnibid.security;

import com.omnibid.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", 3600000L);
    }

    @Test
    void testGenerateAndParseToken() {
        Long userId = 10L;
        UserRole role = UserRole.USER;
        int tokenVersion = 2;

        String token = jwtTokenProvider.generateToken(userId, role, tokenVersion);
        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));

        JwtClaims claims = jwtTokenProvider.parse(token);
        assertEquals(userId, claims.userId());
        assertEquals(role, claims.role());
        assertEquals(tokenVersion, claims.tokenVersion());
        assertNotNull(claims.expiresAt());
    }

    @Test
    void testInvalidToken() {
        assertFalse(jwtTokenProvider.validateToken("invalid.token.string"));
    }
}
