package com.omnibid.config;

import com.omnibid.service.ImageStorageService;
import com.omnibid.service.impl.CloudinaryImageStorageService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityAndTimeoutTest {

    @Test
    void requestIdFilter_GeneratesHeaderPopulatesMdcAndCleansUp() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicReference<String> capturedMdc = new AtomicReference<>();

        FilterChain chain = (request, response) -> capturedMdc.set(MDC.get(RequestIdFilter.MDC_KEY));
        filter.doFilter(req, res, chain);

        String headerVal = res.getHeader(RequestIdFilter.HEADER_NAME);
        assertThat(headerVal).isNotBlank();
        assertThat(capturedMdc.get()).isEqualTo(headerVal);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void requestIdFilter_PropagatesValidIncomingIdAndRejectsUnsafeCharacters() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();

        MockHttpServletRequest validReq = new MockHttpServletRequest();
        validReq.addHeader(RequestIdFilter.HEADER_NAME, "req-abc_123.45");
        MockHttpServletResponse validRes = new MockHttpServletResponse();
        filter.doFilter(validReq, validRes, (r, s) -> {});
        assertThat(validRes.getHeader(RequestIdFilter.HEADER_NAME)).isEqualTo("req-abc_123.45");

        MockHttpServletRequest unsafeReq = new MockHttpServletRequest();
        unsafeReq.addHeader(RequestIdFilter.HEADER_NAME, "bad\r\nInjected-Log");
        MockHttpServletResponse unsafeRes = new MockHttpServletResponse();
        filter.doFilter(unsafeReq, unsafeRes, (r, s) -> {});
        assertThat(unsafeRes.getHeader(RequestIdFilter.HEADER_NAME)).isNotEqualTo("bad\r\nInjected-Log");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void cloudinaryConfig_ConfiguresTimeout() {
        CloudinaryConfig config = new CloudinaryConfig();
        ImageStorageService service = config.imageStorageService("demo-cloud", "key", "secret", 5000, 12000);
        assertThat(service).isInstanceOf(CloudinaryImageStorageService.class);
        assertThat(((CloudinaryImageStorageService) service).getReadTimeoutMs()).isEqualTo(12000);
    }
}
