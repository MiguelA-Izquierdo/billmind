package dev.izquierdo.billmind._shared.infrastructure.ratelimit;

import dev.izquierdo.billmind._shared.infrastructure.auth.ExternalTokenAuthentication;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.config.RateLimitProperties;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.key.IpKeyGenerator;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.key.RateLimitKeyGenerator;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.key.SessionKeyGenerator;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.key.TokenKeyGenerator;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.model.RateLimitDecision;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.model.RateLimitVerdict;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.observability.RateLimitMetrics;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.KeyType;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitPhase;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitPolicy;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitProfile;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.store.RateLimitStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RateLimiterTest {

    private static final String SESSION = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";
    private static final String IP = "1.2.3.4";

    private RateLimitStore store;
    private SimpleMeterRegistry registry;
    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        store = mock(RateLimitStore.class);
        registry = new SimpleMeterRegistry();
        List<RateLimitKeyGenerator> generators =
                List.of(new IpKeyGenerator(false), new SessionKeyGenerator(), new TokenKeyGenerator());
        rateLimiter = new RateLimiter(generators, store, properties(), new RateLimitMetrics(registry));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAllowAndNamespaceTheKeyByProfileAndSession() {
        when(store.tryConsume(anyString(), any())).thenReturn(RateLimitDecision.allowed(19, 20));

        RateLimitVerdict verdict = rateLimiter.check(chatRequest(), RateLimitProfile.CHAT, RateLimitPhase.PRE_AUTH);

        assertThat(verdict.rejected()).isFalse();
        assertThat(verdict.remaining()).isEqualTo(19);
        assertThat(consumedKeys()).contains("chat:sess:" + SESSION);
    }

    /**
     * The heart of the fix: rotating X-Session-Id mints a fresh session bucket, but the IP ceiling
     * underneath is keyed by something the caller cannot rotate, so it counts every request.
     */
    @Test
    void shouldCountEveryRequestAgainstTheIpCeilingEvenWhenTheSessionRotates() {
        when(store.tryConsume(anyString(), any())).thenReturn(RateLimitDecision.allowed(9, 10));

        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest rotated = new MockHttpServletRequest("POST", "/api/v1/invoices");
            rotated.setRemoteAddr(IP);
            rotated.addHeader("X-Session-Id", UUID.randomUUID().toString());
            rateLimiter.check(rotated, RateLimitProfile.UPLOAD, RateLimitPhase.PRE_AUTH);
        }

        verify(store, times(3)).tryConsume(eq("upload:ip:" + IP), any());
    }

    /** The fallback profile guards the routes nobody has reasoned about — it cannot be session-only either. */
    @Test
    void shouldCountAnUnmappedRouteAgainstTheIpCeilingWhenTheSessionRotates() {
        when(store.tryConsume(anyString(), any())).thenReturn(RateLimitDecision.allowed(59, 60));

        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest rotated = new MockHttpServletRequest("GET", "/api/v1/does-not-exist");
            rotated.setRemoteAddr(IP);
            rotated.addHeader("X-Session-Id", UUID.randomUUID().toString());
            rateLimiter.check(rotated, RateLimitProfile.DEFAULT, RateLimitPhase.PRE_AUTH);
        }

        verify(store, times(3)).tryConsume(eq("default:ip:" + IP), any());
    }

    /** The IP ceiling is a real limit, not decoration: exhausting it rejects the request. */
    @Test
    void shouldThrottleWhenTheIpCeilingIsExhaustedThoughTheSessionIsFresh() {
        when(store.tryConsume(eq("upload:sess:" + SESSION), any()))
                .thenReturn(RateLimitDecision.allowed(0, 5));
        when(store.tryConsume(eq("upload:ip:" + IP), any()))
                .thenReturn(RateLimitDecision.denied(10, Duration.ofMinutes(6)));

        RateLimitVerdict verdict = rateLimiter.check(
                uploadRequest(), RateLimitProfile.UPLOAD, RateLimitPhase.PRE_AUTH);

        assertThat(verdict.rejected()).isTrue();
        assertThat(verdict.httpStatus()).isEqualTo(429);
        assertThat(verdict.retryAfter()).isEqualTo(Duration.ofMinutes(6));
    }

    /** Each layer enforces its own bucket — the IP ceiling is far wider than the session budget. */
    @Test
    void shouldEnforceEachLayerWithItsOwnPolicy() {
        when(store.tryConsume(anyString(), any())).thenReturn(RateLimitDecision.allowed(4, 5));

        rateLimiter.check(uploadRequest(), RateLimitProfile.UPLOAD, RateLimitPhase.PRE_AUTH);

        ArgumentCaptor<RateLimitPolicy> policies = ArgumentCaptor.forClass(RateLimitPolicy.class);
        verify(store, times(2)).tryConsume(anyString(), policies.capture());
        assertThat(policies.getAllValues().get(0).capacity()).isEqualTo(5);   // session budget
        assertThat(policies.getAllValues().get(1).capacity()).isEqualTo(10);  // IP ceiling
    }

    /**
     * A visitor who burns their own budget must not also draw from the IP bucket their NAT
     * neighbours share — the first breach wins and the layers below are never consulted.
     */
    @Test
    void shouldNotSpendTheIpCeilingWhenTheSessionBudgetIsAlreadyExhausted() {
        when(store.tryConsume(eq("upload:sess:" + SESSION), any()))
                .thenReturn(RateLimitDecision.denied(5, Duration.ofMinutes(30)));

        RateLimitVerdict verdict = rateLimiter.check(
                uploadRequest(), RateLimitProfile.UPLOAD, RateLimitPhase.PRE_AUTH);

        assertThat(verdict.rejected()).isTrue();
        verify(store, never()).tryConsume(eq("upload:ip:" + IP), any());
    }

    /** CHAT refills 20 tokens per minute, so one missing token is back in 3s. */
    @Test
    void shouldReportTimeToRefillTheBucketAsResetAfter() {
        when(store.tryConsume(anyString(), any())).thenReturn(RateLimitDecision.allowed(19, 20));

        RateLimitVerdict verdict = rateLimiter.check(chatRequest(), RateLimitProfile.CHAT, RateLimitPhase.PRE_AUTH);

        assertThat(verdict.resetAfter()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void shouldReportAFullDrainAsResetAfterWhenThrottled() {
        when(store.tryConsume(eq("chat:sess:" + SESSION), any()))
                .thenReturn(RateLimitDecision.denied(20, Duration.ofSeconds(3)));

        RateLimitVerdict verdict = rateLimiter.check(chatRequest(), RateLimitProfile.CHAT, RateLimitPhase.PRE_AUTH);

        assertThat(verdict.retryAfter()).isEqualTo(Duration.ofSeconds(3));
        assertThat(verdict.resetAfter()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void shouldThrottleWhenStoreDenies() {
        when(store.tryConsume(anyString(), any()))
                .thenReturn(RateLimitDecision.denied(20, Duration.ofSeconds(30)));

        RateLimitVerdict verdict = rateLimiter.check(chatRequest(), RateLimitProfile.CHAT, RateLimitPhase.PRE_AUTH);

        assertThat(verdict.rejected()).isTrue();
        assertThat(verdict.httpStatus()).isEqualTo(429);
        assertThat(verdict.retryAfter()).isEqualTo(Duration.ofSeconds(30));
    }

    /** No session header: that layer is skipped, but the IP ceiling still counts the request. */
    @Test
    void shouldStillEnforceTheIpCeilingWhenSessionHeaderAbsent() {
        when(store.tryConsume(anyString(), any())).thenReturn(RateLimitDecision.allowed(59, 60));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/assistant/chat");
        request.setRemoteAddr(IP);

        RateLimitVerdict verdict = rateLimiter.check(request, RateLimitProfile.CHAT, RateLimitPhase.PRE_AUTH);

        assertThat(verdict.rejected()).isFalse();
        verify(store).tryConsume(eq("chat:ip:" + IP), any());
        verify(store, never()).tryConsume(eq("chat:sess:" + SESSION), any());
    }

    /** A malformed session id is not a bucket: the layer is skipped, the IP ceiling still applies. */
    @Test
    void shouldSkipTheSessionLayerWhenTheHeaderIsNotAUuid() {
        when(store.tryConsume(anyString(), any())).thenReturn(RateLimitDecision.allowed(59, 60));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/assistant/chat");
        request.setRemoteAddr(IP);
        request.addHeader("X-Session-Id", "not-a-uuid");

        rateLimiter.check(request, RateLimitProfile.CHAT, RateLimitPhase.PRE_AUTH);

        assertThat(consumedKeys()).containsExactly("chat:ip:" + IP);
    }

    @Test
    void shouldFailOpenWhenStoreThrowsOnAFailOpenProfile() {
        when(store.tryConsume(anyString(), any())).thenThrow(new RuntimeException("redis down"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/market-rates");
        request.setRemoteAddr(IP);

        RateLimitVerdict verdict = rateLimiter.check(request, RateLimitProfile.PUBLIC_READ, RateLimitPhase.PRE_AUTH);

        assertThat(verdict.rejected()).isFalse();
    }

    @Test
    void shouldFailClosedWhenStoreThrowsOnAFailClosedProfile() {
        when(store.tryConsume(anyString(), any())).thenThrow(new RuntimeException("redis down"));

        RateLimitVerdict verdict = rateLimiter.check(chatRequest(), RateLimitProfile.CHAT, RateLimitPhase.PRE_AUTH);

        assertThat(verdict.rejected()).isTrue();
        assertThat(verdict.httpStatus()).isEqualTo(503);
    }

    @Test
    void shouldEnforceOnlyTheIpLayerOfAdminInThePreAuthPhase() {
        when(store.tryConsume(anyString(), any())).thenReturn(RateLimitDecision.allowed(4, 5));
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/market-rates");
        request.setRemoteAddr("9.9.9.9");
        request.addHeader("Authorization", "Bearer some-token");

        rateLimiter.check(request, RateLimitProfile.ADMIN, RateLimitPhase.PRE_AUTH);

        verify(store).tryConsume(eq("admin:ip:9.9.9.9"), any());
    }

    /** The token layer keys off the identity {@code JwtAuthFilter} validated, not off the header. */
    @Test
    void shouldEnforceOnlyTheTokenLayerOfAdminInThePostAuthPhase() {
        when(store.tryConsume(anyString(), any())).thenReturn(RateLimitDecision.allowed(4, 5));
        SecurityContextHolder.getContext().setAuthentication(ExternalTokenAuthentication.authorized("some-token"));
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/market-rates");
        request.setRemoteAddr("9.9.9.9");

        rateLimiter.check(request, RateLimitProfile.ADMIN, RateLimitPhase.POST_AUTH);

        assertThat(consumedKeys()).singleElement().asString()
                .startsWith("admin:tok:").doesNotContain("some-token");
    }

    /** An unauthenticated caller has no token layer, so a forged token cannot mint a bucket. */
    @Test
    void shouldEnforceNoLayerInThePostAuthPhaseWithoutAValidatedToken() {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/market-rates");
        request.addHeader("Authorization", "Bearer forged-token");

        RateLimitVerdict verdict = rateLimiter.check(request, RateLimitProfile.ADMIN, RateLimitPhase.POST_AUTH);

        assertThat(verdict.rejected()).isFalse();
        verifyNoInteractions(store);
    }

    @Test
    void shouldCountThrottledOutcomeInMetrics() {
        when(store.tryConsume(anyString(), any()))
                .thenReturn(RateLimitDecision.denied(20, Duration.ofSeconds(5)));

        rateLimiter.check(chatRequest(), RateLimitProfile.CHAT, RateLimitPhase.PRE_AUTH);

        double count = registry.counter("ratelimit.requests",
                "profile", "CHAT", "phase", "PRE_AUTH", "outcome", "throttled").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void shouldCountStoreErrorsInMetrics() {
        when(store.tryConsume(anyString(), any())).thenThrow(new RuntimeException("redis down"));

        rateLimiter.check(chatRequest(), RateLimitProfile.CHAT, RateLimitPhase.PRE_AUTH);

        assertThat(registry.counter("ratelimit.store.errors", "profile", "CHAT").count()).isEqualTo(1.0);
    }

    @Test
    void shouldNotEmitMetricsWhenNoLayerApplies() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");

        rateLimiter.check(request, RateLimitProfile.NONE, RateLimitPhase.PRE_AUTH);

        assertThat(registry.find("ratelimit.requests").counters()).isEmpty();
        verifyNoInteractions(store);
    }

    private MockHttpServletRequest chatRequest() {
        return request("POST", "/api/v1/assistant/chat");
    }

    private MockHttpServletRequest uploadRequest() {
        return request("POST", "/api/v1/invoices");
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(IP);
        request.addHeader("X-Session-Id", SESSION);
        return request;
    }

    private List<String> consumedKeys() {
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(store, atLeastOnce()).tryConsume(keys.capture(), any());
        return keys.getAllValues();
    }

    private RateLimitProperties properties() {
        RateLimitProperties properties = new RateLimitProperties();
        Map<RateLimitProfile, RateLimitProperties.Limit> map = new EnumMap<>(RateLimitProfile.class);
        map.put(RateLimitProfile.UPLOAD, withIpCeiling(
                limit(5, 5, Duration.ofHours(1), 5),
                limit(10, 10, Duration.ofHours(1), 1)));
        map.put(RateLimitProfile.CHAT, withIpCeiling(
                limit(20, 20, Duration.ofMinutes(1), 1),
                limit(60, 60, Duration.ofMinutes(1), 1)));
        map.put(RateLimitProfile.DEFAULT, withIpCeiling(
                limit(30, 30, Duration.ofMinutes(1), 1),
                limit(60, 60, Duration.ofMinutes(1), 1)));
        map.put(RateLimitProfile.PUBLIC_READ, limit(60, 60, Duration.ofMinutes(1), 1));
        map.put(RateLimitProfile.ADMIN, limit(5, 5, Duration.ofMinutes(1), 1));
        properties.setProfiles(map);
        return properties;
    }

    private RateLimitProperties.Limit withIpCeiling(RateLimitProperties.Limit base,
                                                    RateLimitProperties.Limit ceiling) {
        base.setOverrides(Map.of(KeyType.IP, ceiling));
        return base;
    }

    private RateLimitProperties.Limit limit(long capacity, long refill, Duration period, long cost) {
        RateLimitProperties.Limit limit = new RateLimitProperties.Limit();
        limit.setCapacity(capacity);
        limit.setRefillTokens(refill);
        limit.setRefillPeriod(period);
        limit.setCost(cost);
        return limit;
    }
}