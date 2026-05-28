package com.rentflow.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void spoofedForwardedHeaderIsIgnoredByDefault() {
        ClientIpResolver resolver = new ClientIpResolver("");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.7, 10.0.0.1");

        String clientIp = resolver.resolve(request);

        assertThat(clientIp).isEqualTo("203.0.113.10");
    }

    @Test
    void trustedProxyCanForwardClientIpWhenConfigured() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.1,10.0.0.2");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.7, 10.0.0.1");

        String clientIp = resolver.resolve(request);

        assertThat(clientIp).isEqualTo("198.51.100.7");
    }

    @Test
    void trustedProxyFallsBackToRemoteAddressWhenHeaderMissing() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");

        String clientIp = resolver.resolve(request);

        assertThat(clientIp).isEqualTo("10.0.0.1");
    }
}
