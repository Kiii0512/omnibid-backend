package com.omnibid.security;

import com.omnibid.enums.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private static final String SECRET =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @Test
    void generateAndParse_ValidToken() {
        Instant now = Instant.parse("2026-11-22T02:00:00Z");
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 86_400_000L, Clock.fixed(now, ZoneOffset.UTC));

        JwtTokenProvider.IssuedToken issued = provider.issueToken(42L, UserRole.USER, 3);
        assertNotNull(issued.accessToken());
        assertEquals(86_400L, issued.expiresInSeconds());
        assertEquals(now.plusSeconds(86_400L), issued.expiresAt());

        JwtClaims claims = provider.parse(issued.accessToken());
        assertEquals(42L, claims.userId());
        assertEquals(UserRole.USER, claims.role());
        assertEquals(3, claims.ver());
        assertEquals(now.plusSeconds(86_400L), claims.expiresAt());
    }

    @Test
    void parse_ExpiredToken_ThrowsExpiredReason() {
        Instant issueTime = Instant.parse("2026-11-22T02:00:00Z");
        JwtTokenProvider issuer = new JwtTokenProvider(SECRET, 1_000L, Clock.fixed(issueTime, ZoneOffset.UTC));
        String token = issuer.generateToken(5L, UserRole.ADMIN, 1);

        JwtTokenProvider verifier = new JwtTokenProvider(
                SECRET, 1_000L, Clock.fixed(issueTime.plus(Duration.ofSeconds(5)), ZoneOffset.UTC));

        JwtValidationException ex = assertThrows(JwtValidationException.class, () -> verifier.parse(token));
        assertEquals(JwtValidationException.Reason.EXPIRED, ex.getReason());
    }

    @Test
    void parse_InvalidSignature_ThrowsInvalidSignatureReason() {
        Instant now = Instant.parse("2026-11-22T02:00:00Z");
        JwtTokenProvider issuer = new JwtTokenProvider(SECRET, 3_600_000L, Clock.fixed(now, ZoneOffset.UTC));
        String token = issuer.generateToken(5L, UserRole.USER, 0);

        String otherSecret = "515E635266556A586E3272357538782F413F4428472B4B6250645367566B5971";
        JwtTokenProvider verifier = new JwtTokenProvider(otherSecret, 3_600_000L, Clock.fixed(now, ZoneOffset.UTC));

        JwtValidationException ex = assertThrows(JwtValidationException.class, () -> verifier.parse(token));
        assertEquals(JwtValidationException.Reason.INVALID_SIGNATURE, ex.getReason());
    }

    @Test
    void parse_MalformedToken_ThrowsMalformedReason() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 3_600_000L, Clock.systemUTC());
        JwtValidationException ex = assertThrows(JwtValidationException.class, () -> provider.parse("invalid.token.string"));
        assertEquals(JwtValidationException.Reason.MALFORMED, ex.getReason());
    }

    @Test
    void constructor_RejectsBlankOrWeakSecretFailFast() {
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenProvider("   ", 3_600_000L, Clock.systemUTC()));
        assertThrows(io.jsonwebtoken.security.WeakKeyException.class,
                () -> new JwtTokenProvider("too-short-secret", 3_600_000L, Clock.systemUTC()));
    }
}
