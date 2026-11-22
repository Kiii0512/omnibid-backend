package com.omnibid.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

@Component
public class PublicEndpointMatcher implements RequestMatcher {

    private record Rule(HttpMethod method, PathPattern pattern) {
        boolean matches(String httpMethod, PathContainer path) {
            if (method != null && !method.matches(httpMethod)) {
                return false;
            }
            return pattern.matches(path);
        }
    }

    private final List<PathPattern> protectedOverrides;
    private final List<Rule> publicRules;

    public PublicEndpointMatcher() {
        PathPatternParser parser = PathPatternParser.defaultInstance;
        this.protectedOverrides = List.of(
                parser.parse("/api/v1/auctions/mine"),
                parser.parse("/api/v1/auctions/mine/**")
        );
        this.publicRules = List.of(
                new Rule(HttpMethod.POST, parser.parse("/api/v1/auth/register")),
                new Rule(HttpMethod.POST, parser.parse("/api/v1/auth/login")),
                new Rule(HttpMethod.GET, parser.parse("/api/v1/categories")),
                new Rule(HttpMethod.GET, parser.parse("/api/v1/auctions")),
                new Rule(HttpMethod.GET, parser.parse("/api/v1/auctions/{id}")),
                new Rule(HttpMethod.GET, parser.parse("/api/v1/auctions/{id}/bids")),
                new Rule(null, parser.parse("/ws")),
                new Rule(null, parser.parse("/ws/**"))
        );
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        String path = extractPath(request);
        if (!StringUtils.hasText(path)) {
            return false;
        }
        PathContainer pathContainer = PathContainer.parsePath(path);
        for (PathPattern excluded : protectedOverrides) {
            if (excluded.matches(pathContainer)) {
                return false;
            }
        }
        String method = request.getMethod();
        for (Rule rule : publicRules) {
            if (rule.matches(method, pathContainer)) {
                return true;
            }
        }
        return false;
    }

    private String extractPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (!StringUtils.hasText(requestUri)) {
            return request.getServletPath();
        }
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }
}
