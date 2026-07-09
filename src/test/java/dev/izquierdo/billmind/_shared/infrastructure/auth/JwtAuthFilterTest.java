package dev.izquierdo.billmind._shared.infrastructure.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.izquierdo.billmind._shared.domain.port.ExternalAuthPort;
import dev.izquierdo.billmind._shared.infrastructure.route.RouteAccessPolicy;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private static final String ADMIN_TOKEN = "Bearer admin-token";

    private ExternalAuthPort externalAuthPort;
    private FilterChain chain;
    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        externalAuthPort = mock(ExternalAuthPort.class);
        chain = mock(FilterChain.class);
        filter = new JwtAuthFilter(externalAuthPort, new RouteAccessPolicy(), new ObjectMapper());
    }

    @ParameterizedTest
    @CsvSource({
            "POST,   /api/v1/admin/knowledge/ingest",
            "POST,   /api/v1/admin/knowledge/ingest/seed",
            "POST,   /api/v1/admin/knowledge/reindex",
            "GET,    /api/v1/admin/knowledge/search",
            "DELETE, /api/v1/admin/knowledge",
            "DELETE, /api/v1/market-rates"
    })
    void shouldRejectAdminRouteWhenNoTokenIsPresent(String method, String uri) throws Exception {
        MockHttpServletResponse response = doFilter(new MockHttpServletRequest(method, uri));

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
        verifyNoInteractions(externalAuthPort);
    }

    @Test
    void shouldRejectKnowledgeIngestWhenTokenIsNotAuthorized() throws Exception {
        when(externalAuthPort.isAuthorized(ADMIN_TOKEN)).thenReturn(false);

        MockHttpServletResponse response = doFilter(adminRequestWithHeader("Authorization", ADMIN_TOKEN));

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void shouldAllowKnowledgeIngestWhenTokenIsAuthorized() throws Exception {
        when(externalAuthPort.isAuthorized(ADMIN_TOKEN)).thenReturn(true);

        MockHttpServletResponse response = doFilter(adminRequestWithHeader("Authorization", ADMIN_TOKEN));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void shouldRejectNonBearerAuthorizationHeaderOnAdminRoute() throws Exception {
        MockHttpServletResponse response = doFilter(adminRequestWithHeader("Authorization", "Basic dXNlcjpwYXNz"));

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
        verifyNoInteractions(externalAuthPort);
    }

    @ParameterizedTest
    @CsvSource({
            "GET,  /api/v1/market-rates",
            "POST, /api/v1/invoices",
            "GET,  /actuator/health"
    })
    void shouldLetNonAdminRouteThroughWithoutToken(String method, String uri) throws Exception {
        MockHttpServletResponse response = doFilter(new MockHttpServletRequest(method, uri));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
        verifyNoInteractions(externalAuthPort);
    }

    private MockHttpServletRequest adminRequestWithHeader(String name, String value) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/knowledge/ingest");
        request.addHeader(name, value);
        return request;
    }

    private MockHttpServletResponse doFilter(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}