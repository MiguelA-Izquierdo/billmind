package dev.izquierdo.billmind._shared.infrastructure.route;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class RouteAccessPolicyTest {

    private final RouteAccessPolicy policy = new RouteAccessPolicy(new RequestPathMatcher());

    @ParameterizedTest
    @CsvSource({
            "POST,   /api/v1/admin/knowledge/ingest",
            "POST,   /api/v1/admin/knowledge/ingest/seed",
            "POST,   /api/v1/admin/knowledge/reindex",
            "GET,    /api/v1/admin/knowledge/search",
            "DELETE, /api/v1/admin/knowledge",
            "DELETE, /api/v1/market-rates"
    })
    void shouldGuardAdminRoutesWithATokenAndNoSession(String method, String uri) {
        assertThat(policy.accessFor(request(method, uri))).isEqualTo(RouteAccess.ADMIN);
    }

    @Test
    void shouldGuardUnregisteredRouteUnderAdminPrefixAsAdmin() {
        assertThat(policy.accessFor(request("PUT", "/api/v1/admin/anything/new"))).isEqualTo(RouteAccess.ADMIN);
    }

    @ParameterizedTest
    @CsvSource({
            "GET,  /api/v1/invoices",
            "POST, /api/v1/invoices",
            "GET,  /api/v1/invoices/42/comparison",
            "POST, /api/v1/assistant/chat"
    })
    void shouldRequireASessionOnVisitorRoutes(String method, String uri) {
        assertThat(policy.accessFor(request(method, uri))).isEqualTo(RouteAccess.ANONYMOUS);
    }

    @Test
    void shouldDefaultUnrecognizedApiRouteToAnonymous() {
        assertThat(policy.accessFor(request("GET", "/api/v1/something-new"))).isEqualTo(RouteAccess.ANONYMOUS);
    }

    @Test
    void shouldReadMarketRatesWithoutTokenOrSession() {
        assertThat(policy.accessFor(request("GET", "/api/v1/market-rates"))).isEqualTo(RouteAccess.OPEN);
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /actuator/health",
            "GET, /chat/index.html",
            "GET, /"
    })
    void shouldLeaveNonApiRoutesOpen(String method, String uri) {
        assertThat(policy.accessFor(request(method, uri))).isEqualTo(RouteAccess.OPEN);
    }

    @Test
    void shouldNotTreatPathThatMerelyStartsWithAdminTextAsAdmin() {
        assertThat(policy.accessFor(request("GET", "/api/v1/administrators"))).isEqualTo(RouteAccess.ANONYMOUS);
    }

    private MockHttpServletRequest request(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }
}