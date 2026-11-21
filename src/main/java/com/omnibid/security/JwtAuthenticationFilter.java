package com.omnibid.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnibid.common.ApiResponse;
import com.omnibid.common.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveToken(request);
        boolean isPublic = SecurityPaths.PUBLIC_REQUEST_MATCHER.matches(request);

        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        JwtClaims claims;
        try {
            claims = jwtTokenProvider.parseClaims(token);
        } catch (Exception ex) {
            if (isPublic) {
                filterChain.doFilter(request, response);
                return;
            }
            writeError(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Token không hợp lệ hoặc đã hết hạn");
            return;
        }

        CustomUserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserById(claims.userId());
        } catch (Exception ex) {
            if (isPublic) {
                filterChain.doFilter(request, response);
                return;
            }
            writeError(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Người dùng không tồn tại");
            return;
        }

        if (userDetails.isBanned()) {
            writeError(response, HttpStatus.FORBIDDEN, ErrorCode.ACCOUNT_BANNED, "Tài khoản của bạn đã bị khóa");
            return;
        }

        if (claims.tokenVersion() != userDetails.getTokenVersion()) {
            if (isPublic) {
                filterChain.doFilter(request, response);
                return;
            }
            writeError(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Phiên đăng nhập đã hết hiệu lực, vui lòng đăng nhập lại");
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7).trim();
        }
        return null;
    }

    private void writeError(HttpServletResponse response, HttpStatus status, ErrorCode code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Void> apiResponse = ApiResponse.error(code, message);
        objectMapper.writeValue(response.getOutputStream(), apiResponse);
    }
}
