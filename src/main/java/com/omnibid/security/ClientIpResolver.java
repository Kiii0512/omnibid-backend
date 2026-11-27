package com.omnibid.security;

import com.omnibid.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    private final AppProperties appProperties;

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String remoteAddr = normalize(request.getRemoteAddr());
        Set<String> trustedProxies = parseTrustedProxies();
        if (trustedProxies.isEmpty() || !trustedProxies.contains(remoteAddr)) {
            return remoteAddr;
        }

        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            String[] hops = xff.split(",");
            // Traverse right-to-left and return the first hop that is NOT a trusted proxy
            for (int i = hops.length - 1; i >= 0; i--) {
                String candidate = normalize(hops[i]);
                if (!candidate.isEmpty() && !trustedProxies.contains(candidate)) {
                    return candidate;
                }
            }
        }

        String realIp = normalize(request.getHeader("X-Real-IP"));
        if (!realIp.isEmpty() && !trustedProxies.contains(realIp)) {
            return realIp;
        }

        return remoteAddr;
    }

    private Set<String> parseTrustedProxies() {
        String raw = appProperties.getSecurity().getLogin().getTrustedProxies();
        if (!StringUtils.hasText(raw)) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(this::normalize)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private String normalize(String ip) {
        return ip == null ? "" : ip.trim();
    }
}
