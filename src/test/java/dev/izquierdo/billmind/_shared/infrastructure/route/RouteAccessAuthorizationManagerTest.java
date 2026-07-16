package dev.izquierdo.billmind._shared.infrastructure.route;

import dev.izquierdo.billmind._shared.infrastructure.auth.ExternalTokenAuthentication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class RouteAccessAuthorizationManagerTest {

    private final RouteAccessAuthorizationManager manager =
            new RouteAccessAuthorizationManager(new RouteAccessPolicy(new RequestPathMatcher()));

    @ParameterizedTest
    @CsvSource({
            "POST,   /api/v1/admin/knowledge/ingest",
            "GET,    /api/v1/admin/knowledge/search",
            "DELETE, /api/v1/market-rates"
    })
    void shouldDenyAdminRouteToAnAnonymousCaller(String method, String uri) {
        assertThat(granted(method, uri, anonymous())).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "POST,   /api/v1/admin/knowledge/ingest",
            "DELETE, /api/v1/market-rates"
    })
    void shouldGrantAdminRouteToAnAuthorizedToken(String method, String uri) {
        assertThat(granted(method, uri, ExternalTokenAuthentication.authorized("valid"))).isTrue();
    }

    @Test
    void shouldDenyAdminRouteToARejectedToken() {
        assertThat(granted("POST", "/api/v1/admin/knowledge/ingest", ExternalTokenAuthentication.rejected("forged")))
                .isFalse();
    }

    @Test
    void shouldDenyAdminRouteWhenThereIsNoAuthenticationAtAll() {
        assertThat(granted("POST", "/api/v1/admin/knowledge/ingest", null)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "GET,  /api/v1/market-rates",
            "POST, /api/v1/invoices",
            "GET,  /api/v1/invoices/1",
            "GET,  /chat/index.html"
    })
    void shouldGrantEveryNonAdminRouteWithoutAuthentication(String method, String uri) {
        assertThat(granted(method, uri, anonymous())).isTrue();
    }

    private boolean granted(String method, String uri, Authentication authentication) {
        Supplier<Authentication> supplier = () -> authentication;
        RequestAuthorizationContext context =
                new RequestAuthorizationContext(new MockHttpServletRequest(method, uri));
        return manager.check(supplier, context).isGranted();
    }

    /** What {@code AnonymousAuthenticationFilter} leaves behind: authenticated, but with no admin authority. */
    private Authentication anonymous() {
        return new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    }
}