package com.omnibid.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

public final class SecurityPaths {

    private SecurityPaths() {}

    public static final RequestMatcher PUBLIC_REQUEST_MATCHER = new OrRequestMatcher(
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/register"),
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/login"),
            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/api/v1/categories"),
            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/api/v1/categories/**"),
            PathPatternRequestMatcher.pathPattern("/ws"),
            PathPatternRequestMatcher.pathPattern("/ws/**"),
            new AndRequestMatcher(
                    new OrRequestMatcher(
                            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/api/v1/auctions"),
                            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/api/v1/auctions/**")
                    ),
                    new NegatedRequestMatcher(PathPatternRequestMatcher.pathPattern("/api/v1/auctions/mine")),
                    new NegatedRequestMatcher(PathPatternRequestMatcher.pathPattern("/api/v1/auctions/mine/**"))
            )
    );
}
