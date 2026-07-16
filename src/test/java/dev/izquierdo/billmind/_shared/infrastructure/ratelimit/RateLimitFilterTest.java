package dev.izquierdo.billmind._shared.infrastructure.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.model.RateLimitVerdict;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitPhase;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitPolicyResolver;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitProfile;
import dev.izquierdo.billmind._shared.infrastructure.route.RequestPathMatcher;
import dev.izquierdo.billmind._shared.infrastructure.route.RouteAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private RateLimiter rateLimiter;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        rateLimiter = mock(RateLimiter.class);
        RequestPathMatcher pathMatcher = new RequestPathMatcher();
        RateLimitPolicyResolver resolver =
                new RateLimitPolicyResolver(new RouteAccessPolicy(pathMatcher), pathMatcher);
        filter = new RateLimitFilter(rateLimiter, resolver, new ObjectMapper());
    }

    @Test
    void shouldPassThroughAndSetHeadersWhenAllowed() throws Exception {
        when(rateLimiter.check(any(), eq(RateLimitProfile.CHAT), eq(RateLimitPhase.PRE_AUTH)))
                .thenReturn(RateLimitVerdict.allowed(20, 19, Duration.ofSeconds(3)));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(chatRequest(), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("20");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("19");
        assertThat(response.getHeader("X-RateLimit-Reset")).isEqualTo("3");
    }

    @Test
    void shouldReturn429WithRetryAfterAndSpanishBodyWhenThrottled() throws Exception {
        when(rateLimiter.check(any(), eq(RateLimitProfile.CHAT), eq(RateLimitPhase.PRE_AUTH)))
                .thenReturn(RateLimitVerdict.throttled(20, Duration.ofSeconds(30), Duration.ofSeconds(60)));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(chatRequest(), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("30");
        assertThat(response.getHeader("X-RateLimit-Reset")).isEqualTo("60");
        assertThat(response.getContentAsString()).contains("Has superado el límite");
    }

    @Test
    void shouldRoundRetryAfterUpToTheNextWholeSecond() throws Exception {
        when(rateLimiter.check(any(), eq(RateLimitProfile.CHAT), eq(RateLimitPhase.PRE_AUTH)))
                .thenReturn(RateLimitVerdict.throttled(20, Duration.ofMillis(1200), Duration.ofSeconds(60)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(chatRequest(), response, new MockFilterChain());

        assertThat(response.getHeader("Retry-After")).isEqualTo("2");
    }

    @Test
    void shouldReturn503WhenStoreUnavailableUnderFailClosed() throws Exception {
        when(rateLimiter.check(any(), eq(RateLimitProfile.CHAT), eq(RateLimitPhase.PRE_AUTH)))
                .thenReturn(RateLimitVerdict.unavailable());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(chatRequest(), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("no está disponible");
    }

    @Test
    void shouldNotFilterUnlimitedRoutes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getHeader("X-RateLimit-Limit")).isNull();
    }

    private MockHttpServletRequest chatRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/assistant/chat");
        request.addHeader("X-Session-Id", "abc");
        return request;
    }
}