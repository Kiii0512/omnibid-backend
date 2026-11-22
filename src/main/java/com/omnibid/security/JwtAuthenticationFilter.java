package com.omnibid.security;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final PublicEndpointMatcher publicEndpointMatcher;
    private final JsonMapper jsonMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String bearerToken = request.getHeader("Authorization");
        if (!StringUtils.hasText(bearerToken) || !bearerToken.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = bearerToken.substring(7).trim();
        try {
            JwtClaims claims = jwtTokenProvider.parse(token);
            CustomUserDetails userDetails = userDetailsService.loadUserById(claims.userId());

            if (claims.ver() != userDetails.getTokenVersion()) {
                log.debug("JWT token version mismatch for userId={}", claims.userId());
                if (!handleInvalidToken(request, response)) {
                    return;
                }
                filterChain.doFilter(request, response);
                return;
            }

            if (userDetails.isBanned()) {
                log.warn("Blocked request for banned userId={}", userDetails.getId());
                SecurityContextHolder.clearContext();
                writeErrorResponse(response, ErrorCode.ACCOUNT_BANNED);
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (JwtValidationException | UsernameNotFoundException ex) {
            log.debug("JWT authentication rejected: {}", ex.getMessage());
            if (!handleInvalidToken(request, response)) {
                return;
            }
            filterChain.doFilter(request, response);
        }
    }

    private boolean handleInvalidToken(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        if (publicEndpointMatcher.matches(request)) {
            return true;
        }
        writeErrorResponse(response, ErrorCode.UNAUTHORIZED);
        return false;
    }

    private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getWriter(), ApiResponse.error(errorCode));
    }
}
