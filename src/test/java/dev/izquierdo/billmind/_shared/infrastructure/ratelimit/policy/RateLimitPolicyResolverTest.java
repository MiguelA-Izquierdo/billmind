package dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy;

import dev.izquierdo.billmind._shared.infrastructure.route.RequestPathMatcher;
import dev.izquierdo.billmind._shared.infrastructure.route.RouteAccessPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitPolicyResolverTest {

    private final RequestPathMatcher pathMatcher = new RequestPathMatcher();
    private final RateLimitPolicyResolver resolver =
            new RateLimitPolicyResolver(new RouteAccessPolicy(pathMatcher), pathMatcher);

    @Test
    void shouldClassifyUploadAsUpload() {
        assertThat(resolver.profileFor(request("POST", "/api/v1/invoices")))
                .isEqualTo(RateLimitProfile.UPLOAD);
    }

    @Test
    void shouldClassifyChatAsChat() {
        assertThat(resolver.profileFor(request("POST", "/api/v1/assistant/chat")))
                .isEqualTo(RateLimitProfile.CHAT);
    }

    @ParameterizedTest
    @CsvSource({
            "POST,   /api/v1/admin/knowledge/ingest",
            "GET,    /api/v1/admin/knowledge/search",
            "PUT,    /api/v1/admin/anything/new",
            "DELETE, /api/v1/market-rates"
    })
    void shouldClassifyAdminRoutesAsAdmin(String method, String uri) {
        assertThat(resolver.profileFor(request(method, uri))).isEqualTo(RateLimitProfile.ADMIN);
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/v1/market-rates",
            "GET, /api/v1/invoices",
            "GET, /api/v1/invoices/42",
            "GET, /api/v1/invoices/42/comparison"
    })
    void shouldClassifyCheapReadsAsPublicRead(String method, String uri) {
        assertThat(resolver.profileFor(request(method, uri))).isEqualTo(RateLimitProfile.PUBLIC_READ);
    }

    @ParameterizedTest
    @CsvSource({
            "GET,  /api/v1/something-new",
            "PATCH, /api/v1/invoices/42"
    })
    void shouldDefaultUnmappedApiRoutesToDefault(String method, String uri) {
        assertThat(resolver.profileFor(request(method, uri))).isEqualTo(RateLimitProfile.DEFAULT);
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /actuator/health",
            "GET, /chat/index.html",
            "GET, /"
    })
    void shouldNotLimitNonApiRoutes(String method, String uri) {
        assertThat(resolver.profileFor(request(method, uri))).isEqualTo(RateLimitProfile.NONE);
    }

    private MockHttpServletRequest request(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }
}