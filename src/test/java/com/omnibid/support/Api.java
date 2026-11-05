package com.omnibid.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnibid.common.ApiResponse;
import com.omnibid.entity.User;
import com.omnibid.security.JwtTokenProvider;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class Api {

    private int port;

    private final RestTemplate restTemplate;
    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ThreadLocal<String> tokenHolder = new ThreadLocal<>();

    public Api(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.restTemplate = new RestTemplate(new org.springframework.http.client.JdkClientHttpRequestFactory());
        this.restTemplate.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
        });
    }

    public void setPort(int port) {
        this.port = port;
    }

    private String toFullUrl(String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        if (!path.startsWith("/api/v1")) {
            path = "/api/v1" + (path.startsWith("/") ? path : "/" + path);
        }
        return "http://localhost:" + port + path;
    }

    public Api as(User user) {
        if (user != null) {
            tokenHolder.set(jwtTokenProvider.generateToken(user.getEmail(), user.getRole().name()));
        } else {
            tokenHolder.remove();
        }
        return this;
    }

    public Api asToken(String token) {
        tokenHolder.set(token);
        return this;
    }

    public Api anonymous() {
        tokenHolder.remove();
        return this;
    }

    private HttpHeaders createHeaders(Map<String, String> extraHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String token = tokenHolder.get();
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
        if (extraHeaders != null) {
            extraHeaders.forEach(headers::set);
        }
        return headers;
    }

    public ResponseEntity<String> get(String url) {
        return get(url, Map.of());
    }

    public ResponseEntity<String> get(String url, Map<String, String> headers) {
        HttpEntity<?> entity = new HttpEntity<>(createHeaders(headers));
        return restTemplate.exchange(toFullUrl(url), HttpMethod.GET, entity, String.class);
    }

    public <T> ResponseEntity<ApiResponse<T>> get(String url, ParameterizedTypeReference<ApiResponse<T>> responseType) {
        HttpEntity<?> entity = new HttpEntity<>(createHeaders(Map.of()));
        return restTemplate.exchange(toFullUrl(url), HttpMethod.GET, entity, responseType);
    }

    public ResponseEntity<String> post(String url, Object body) {
        return post(url, body, Map.of());
    }

    public ResponseEntity<String> post(String url, Object body, Map<String, String> headers) {
        HttpEntity<?> entity = new HttpEntity<>(body, createHeaders(headers));
        return restTemplate.exchange(toFullUrl(url), HttpMethod.POST, entity, String.class);
    }

    public ResponseEntity<String> patch(String url, Object body) {
        return patch(url, body, Map.of());
    }

    public ResponseEntity<String> patch(String url, Object body, Map<String, String> headers) {
        HttpEntity<?> entity = new HttpEntity<>(body, createHeaders(headers));
        return restTemplate.exchange(toFullUrl(url), HttpMethod.PATCH, entity, String.class);
    }

    public ResponseEntity<String> put(String url, Object body) {
        HttpEntity<?> entity = new HttpEntity<>(body, createHeaders(Map.of()));
        return restTemplate.exchange(toFullUrl(url), HttpMethod.PUT, entity, String.class);
    }

    public ResponseEntity<String> delete(String url) {
        HttpEntity<?> entity = new HttpEntity<>(createHeaders(Map.of()));
        return restTemplate.exchange(toFullUrl(url), HttpMethod.DELETE, entity, String.class);
    }

    public ResponseEntity<String> options(String url, Map<String, String> headers) {
        HttpEntity<?> entity = new HttpEntity<>(createHeaders(headers));
        return restTemplate.exchange(toFullUrl(url), HttpMethod.OPTIONS, entity, String.class);
    }
}
