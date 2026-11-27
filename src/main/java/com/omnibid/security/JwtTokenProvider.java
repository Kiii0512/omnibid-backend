package com.omnibid.security;

import com.omnibid.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long jwtExpirationMs;
    private final Clock clock;

    @Autowired
    public JwtTokenProvider(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.expiration-ms:86400000}") long jwtExpirationMs,
            Clock clock
    ) {
        this.signingKey = buildSigningKey(jwtSecret);
        this.jwtExpirationMs = jwtExpirationMs;
        this.clock = clock;
    }

    public record IssuedToken(String accessToken, long expiresInSeconds, Instant expiresAt) {}

    private static SecretKey buildSigningKey(String jwtSecret) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalArgumentException("jwt.secret must not be blank");
        }
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtSecret);
        } catch (Exception e) {
            keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Long userId, UserRole role, int tokenVersion) {
        return issueToken(userId, role, tokenVersion).accessToken();
    }

    public IssuedToken issueToken(Long userId, UserRole role, int tokenVersion) {
        Instant now = clock.instant();
        Instant expiresAt = now.plusMillis(jwtExpirationMs);

        String token = Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("role", role.name())
                .claim("ver", tokenVersion)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiresAt))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();

        return new IssuedToken(token, jwtExpirationMs / 1000L, expiresAt);
    }

    public JwtClaims parse(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtValidationException(JwtValidationException.Reason.MALFORMED, "JWT token is empty");
        }
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .setClock(() -> Date.from(clock.instant()))
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String subject = claims.getSubject();
            String roleStr = claims.get("role", String.class);
            Integer ver = claims.get("ver", Integer.class);
            Date expiration = claims.getExpiration();

            if (subject == null || roleStr == null || ver == null || expiration == null) {
                throw new JwtValidationException(JwtValidationException.Reason.INVALID_CLAIMS, "Missing required JWT claims");
            }

            Long userId = Long.parseLong(subject);
            UserRole role = UserRole.valueOf(roleStr);
            return new JwtClaims(userId, role, ver, expiration.toInstant());
        } catch (JwtValidationException ex) {
            throw ex;
        } catch (ExpiredJwtException ex) {
            throw new JwtValidationException(JwtValidationException.Reason.EXPIRED, "JWT token has expired", ex);
        } catch (SecurityException ex) {
            throw new JwtValidationException(JwtValidationException.Reason.INVALID_SIGNATURE, "Invalid JWT signature", ex);
        } catch (MalformedJwtException ex) {
            throw new JwtValidationException(JwtValidationException.Reason.MALFORMED, "Malformed JWT token", ex);
        } catch (UnsupportedJwtException ex) {
            throw new JwtValidationException(JwtValidationException.Reason.UNSUPPORTED, "Unsupported JWT token", ex);
        } catch (IllegalArgumentException ex) {
            throw new JwtValidationException(JwtValidationException.Reason.INVALID_CLAIMS, "Invalid JWT claims", ex);
        }
    }

    public long getExpirationSeconds() {
        return jwtExpirationMs / 1000L;
    }
}
