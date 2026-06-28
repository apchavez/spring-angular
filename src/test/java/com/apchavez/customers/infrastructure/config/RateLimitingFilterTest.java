package com.apchavez.customers.infrastructure.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitingFilterTest {

    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitingFilter();
    }

    private MockServerWebExchange buildExchange(HttpMethod method, String path, String ip) {
        MockServerHttpRequest request = MockServerHttpRequest
                .method(method, path)
                .remoteAddress(new InetSocketAddress(ip, 80))
                .build();
        return MockServerWebExchange.from(request);
    }

    private WebFilterChain passThroughChain() {
        return exchange -> Mono.empty();
    }

    // ── GET is not rate-limited ──────────────────────────────────────────────

    @Test
    void should_allow_get_requests_without_counting() {
        MockServerWebExchange exchange = buildExchange(HttpMethod.GET, "/api/v1/customers/active", "1.1.1.1");

        StepVerifier.create(filter.filter(exchange, passThroughChain()))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    // ── POST is rate-limited ─────────────────────────────────────────────────

    @Test
    void should_allow_post_request_within_limit() {
        MockServerWebExchange exchange = buildExchange(HttpMethod.POST, "/api/v1/customers", "2.2.2.2");

        StepVerifier.create(filter.filter(exchange, passThroughChain()))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void should_block_post_request_when_limit_exceeded() {
        String ip = "3.3.3.3";
        WebFilterChain chain = passThroughChain();

        for (int i = 0; i < RateLimitingFilter.MAX_REQUESTS; i++) {
            filter.filter(buildExchange(HttpMethod.POST, "/api/v1/customers", ip), chain).block();
        }

        MockServerWebExchange blocked = buildExchange(HttpMethod.POST, "/api/v1/customers", ip);
        filter.filter(blocked, chain).block();

        assertThat(blocked.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getResponse().getHeaders().getFirst("Retry-After")).isNotNull();
    }

    // ── PUT is rate-limited ──────────────────────────────────────────────────

    @Test
    void should_allow_put_request_within_limit() {
        MockServerWebExchange exchange = buildExchange(HttpMethod.PUT, "/api/v1/customers/1", "6.6.6.6");

        StepVerifier.create(filter.filter(exchange, passThroughChain()))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void should_block_put_request_when_limit_exceeded() {
        String ip = "7.7.7.7";
        WebFilterChain chain = passThroughChain();

        for (int i = 0; i < RateLimitingFilter.MAX_REQUESTS; i++) {
            filter.filter(buildExchange(HttpMethod.PUT, "/api/v1/customers/1", ip), chain).block();
        }

        MockServerWebExchange blocked = buildExchange(HttpMethod.PUT, "/api/v1/customers/1", ip);
        filter.filter(blocked, chain).block();

        assertThat(blocked.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getResponse().getHeaders().getFirst("Retry-After")).isNotNull();
    }

    // ── DELETE is rate-limited ───────────────────────────────────────────────

    @Test
    void should_allow_delete_request_within_limit() {
        MockServerWebExchange exchange = buildExchange(HttpMethod.DELETE, "/api/v1/customers/1", "8.8.8.8");

        StepVerifier.create(filter.filter(exchange, passThroughChain()))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void should_block_delete_request_when_limit_exceeded() {
        String ip = "9.9.9.9";
        WebFilterChain chain = passThroughChain();

        for (int i = 0; i < RateLimitingFilter.MAX_REQUESTS; i++) {
            filter.filter(buildExchange(HttpMethod.DELETE, "/api/v1/customers/1", ip), chain).block();
        }

        MockServerWebExchange blocked = buildExchange(HttpMethod.DELETE, "/api/v1/customers/1", ip);
        filter.filter(blocked, chain).block();

        assertThat(blocked.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getResponse().getHeaders().getFirst("Retry-After")).isNotNull();
    }

    // ── Per-IP isolation ─────────────────────────────────────────────────────

    @Test
    void should_track_limits_independently_per_ip() {
        String ip1 = "4.4.4.4";
        String ip2 = "5.5.5.5";
        WebFilterChain chain = passThroughChain();

        for (int i = 0; i < RateLimitingFilter.MAX_REQUESTS; i++) {
            filter.filter(buildExchange(HttpMethod.POST, "/api/v1/customers", ip1), chain).block();
        }
        MockServerWebExchange blockedIp1 = buildExchange(HttpMethod.POST, "/api/v1/customers", ip1);
        filter.filter(blockedIp1, chain).block();
        assertThat(blockedIp1.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        MockServerWebExchange allowedIp2 = buildExchange(HttpMethod.POST, "/api/v1/customers", ip2);
        filter.filter(allowedIp2, chain).block();
        assertThat(allowedIp2.getResponse().getStatusCode()).isNull();
    }

    // ── X-Forwarded-For: rightmost IP is used ────────────────────────────────

    @Test
    void should_use_rightmost_ip_from_x_forwarded_for_header() {
        String spoofedIp = "1.2.3.4";
        String trustedIp = "10.10.10.10";
        WebFilterChain chain = passThroughChain();

        for (int i = 0; i < RateLimitingFilter.MAX_REQUESTS; i++) {
            MockServerHttpRequest req = MockServerHttpRequest
                    .method(HttpMethod.POST, "/api/v1/customers")
                    .remoteAddress(new InetSocketAddress("127.0.0.1", 80))
                    .header("X-Forwarded-For", spoofedIp + ", " + trustedIp)
                    .build();
            filter.filter(MockServerWebExchange.from(req), chain).block();
        }

        // 101st request — same last IP → blocked
        MockServerWebExchange blocked = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.POST, "/api/v1/customers")
                        .remoteAddress(new InetSocketAddress("127.0.0.1", 80))
                        .header("X-Forwarded-For", spoofedIp + ", " + trustedIp)
                        .build());
        filter.filter(blocked, chain).block();
        assertThat(blocked.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
