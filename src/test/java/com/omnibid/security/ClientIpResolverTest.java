package com.omnibid.security;

import com.omnibid.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void ignoresSpoofedXffWhenNoTrustedProxiesConfigured() {
        AppProperties props = new AppProperties();
        ClientIpResolver resolver = new ClientIpResolver(props);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("203.0.113.10");
        req.addHeader("X-Forwarded-For", "1.2.3.4");

        assertThat(resolver.resolve(req)).isEqualTo("203.0.113.10");
    }

    @Test
    void extractsRightmostUntrustedIpWhenBehindTrustedProxy() {
        AppProperties props = new AppProperties();
        props.getSecurity().getLogin().setTrustedProxies("10.0.0.1, 10.0.0.2");
        ClientIpResolver resolver = new ClientIpResolver(props);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        // Client spoofed "1.2.3.4", proxy appended real client IP "198.51.100.77" and internal hop "10.0.0.2"
        req.addHeader("X-Forwarded-For", "1.2.3.4, 198.51.100.77, 10.0.0.2");

        assertThat(resolver.resolve(req)).isEqualTo("198.51.100.77");
    }

    @Test
    void ignoresXffWhenRemoteAddrIsNotTrustedProxy() {
        AppProperties props = new AppProperties();
        props.getSecurity().getLogin().setTrustedProxies("10.0.0.1");
        ClientIpResolver resolver = new ClientIpResolver(props);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("198.51.100.99");
        req.addHeader("X-Forwarded-For", "1.2.3.4");

        assertThat(resolver.resolve(req)).isEqualTo("198.51.100.99");
    }
}
