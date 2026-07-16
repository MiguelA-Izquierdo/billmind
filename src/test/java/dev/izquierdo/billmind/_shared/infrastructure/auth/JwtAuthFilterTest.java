package dev.izquierdo.billmind._shared.infrastructure.auth;

import dev.izquierdo.billmind._shared.domain.port.ExternalAuthPort;
import dev.izquierdo.billmind._shared.infrastructure.route.RequestPathMatcher;
import dev.izquierdo.billmind._shared.infrastructure.route.RouteAccessPolicy;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The filter authenticates and nothing else — it always continues the chain, and every rejection is
 * asserted a layer up (see {@code AdminRouteAuthorizationTest}). What matters here is the identity it
 * leaves behind, because the {@code 401}/{@code 403} split and the token-keyed rate limit both read it.
 */
class JwtAuthFilterTest {

    private static final String ADMIN_ROUTE = "/api/v1/admin/knowledge/ingest";
    private static final String ADMIN_TOKEN = "Bearer admin-token";

    private ExternalAuthPort externalAuthPort;
    private FilterChain chain;
    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        externalAuthPort = mock(ExternalAuthPort.class);
        chain = mock(FilterChain.class);
        filter = new JwtAuthFilter(externalAuthPort, new RouteAccessPolicy(new RequestPathMatcher()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldGrantAdminAuthorityWhenTokenIsAuthorized() throws Exception {
        when(externalAuthPort.isAuthorized(ADMIN_TOKEN)).thenReturn(true);

        doFilter(adminRequestWithHeader("Authorization", ADMIN_TOKEN));

        assertThat(authentication()).isInstanceOfSatisfying(ExternalTokenAuthentication.class,
                token -> assertThat(token.isAdmin()).isTrue());
        assertThat(authentication().getAuthorities()).extracting("authority").containsExactly(Roles.ADMIN);
        verify(chain).doFilter(any(), any());
    }

    /**
     * A refused token grants nothing and is not authenticated — otherwise a future
     * {@code authenticated()} rule would wave it through. It is still left in the context, because its
     * presence is what turns the denial into a {@code 403} instead of a {@code 401}.
     */
    @Test
    void shouldLeaveARejectedTokenUnauthenticatedAndWithoutAuthorities() throws Exception {
        when(externalAuthPort.isAuthorized(ADMIN_TOKEN)).thenReturn(false);

        doFilter(adminRequestWithHeader("Authorization", ADMIN_TOKEN));

        assertThat(authentication()).isInstanceOfSatisfying(ExternalTokenAuthentication.class,
                token -> assertThat(token.isAdmin()).isFalse());
        assertThat(authentication().isAuthenticated()).isFalse();
        assertThat(authentication().getAuthorities()).isEmpty();
        verify(chain).doFilter(any(), any());
    }

    /** Rule #6: the raw token must not survive anywhere, not even as the principal. */
    @Test
    void shouldNeverExposeTheRawTokenAsPrincipal() throws Exception {
        when(externalAuthPort.isAuthorized(ADMIN_TOKEN)).thenReturn(true);

        doFilter(adminRequestWithHeader("Authorization", ADMIN_TOKEN));

        assertThat(authentication().getPrincipal().toString()).isNotEmpty().doesNotContain("admin-token");
    }

    @Test
    void shouldLeaveContextAnonymousWhenNoTokenIsPresent() throws Exception {
        doFilter(new MockHttpServletRequest("POST", ADMIN_ROUTE));

        assertThat(authentication()).isNull();
        verify(chain).doFilter(any(), any());
        verifyNoInteractions(externalAuthPort);
    }

    @Test
    void shouldLeaveContextAnonymousWhenAuthorizationIsNotBearer() throws Exception {
        doFilter(adminRequestWithHeader("Authorization", "Basic dXNlcjpwYXNz"));

        assertThat(authentication()).isNull();
        verifyNoInteractions(externalAuthPort);
    }

    /** A non-admin route never reaches the introspection endpoint, even carrying a bearer token. */
    @ParameterizedTest
    @CsvSource({
            "GET,  /api/v1/market-rates",
            "POST, /api/v1/invoices",
            "GET,  /actuator/health"
    })
    void shouldNotIntrospectOnNonAdminRoutes(String method, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("Authorization", ADMIN_TOKEN);

        doFilter(request);

        assertThat(authentication()).isNull();
        verify(chain).doFilter(any(), any());
        verifyNoInteractions(externalAuthPort);
    }

    private Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private MockHttpServletRequest adminRequestWithHeader(String name, String value) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", ADMIN_ROUTE);
        request.addHeader(name, value);
        return request;
    }

    private MockHttpServletResponse doFilter(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}