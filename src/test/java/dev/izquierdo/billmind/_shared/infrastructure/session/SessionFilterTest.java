package dev.izquierdo.billmind._shared.infrastructure.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.izquierdo.billmind._shared.infrastructure.route.RequestPathMatcher;
import dev.izquierdo.billmind._shared.infrastructure.route.RouteAccessPolicy;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SessionFilterTest {

    private SessionService sessionService;
    private SessionContext sessionContext;
    private FilterChain chain;
    private SessionFilter filter;

    @BeforeEach
    void setUp() {
        sessionService = mock(SessionService.class);
        sessionContext = mock(SessionContext.class);
        chain = mock(FilterChain.class);
        filter = new SessionFilter(sessionService, sessionContext, new ObjectMapper(), new RouteAccessPolicy(new RequestPathMatcher()));
    }

    @Test
    void shouldPassThroughAndUpsertSessionWhenTheRequestCreatesState() throws Exception {
        UUID sessionId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/invoices");
        request.addHeader("X-Session-Id", sessionId.toString());

        MockHttpServletResponse response = doFilter(request);

        verify(sessionService).upsert(sessionId);
        verify(sessionContext).setSessionId(sessionId);
        verify(chain).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    /**
     * A read must not write. Otherwise a forged {@code X-Session-Id} on any GET — including a path
     * that answers 404 — inserts a row, and an unauthenticated caller grows the table at will.
     */
    @ParameterizedTest
    @CsvSource({
            "/api/v1/invoices",
            "/api/v1/invoices/8b1a9953-4c2e-4d1a-9f3a-1b2c3d4e5f60",
            "/api/v1/does-not-exist"
    })
    void shouldBindTheSessionWithoutWritingItOnAReadRequest(String uri) throws Exception {
        UUID sessionId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.addHeader("X-Session-Id", sessionId.toString());

        MockHttpServletResponse response = doFilter(request);

        verify(sessionContext).setSessionId(sessionId);
        verify(chain).doFilter(any(), any());
        verifyNoInteractions(sessionService);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldRejectWithMissingHeader() throws Exception {
        MockHttpServletResponse response = doFilter(new MockHttpServletRequest("POST", "/api/v1/invoices"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("La cabecera X-Session-Id es obligatoria");
        verify(chain, never()).doFilter(any(), any());
        verifyNoInteractions(sessionService, sessionContext);
    }

    @Test
    void shouldRejectWithInvalidUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/invoices");
        request.addHeader("X-Session-Id", "not-a-uuid");

        MockHttpServletResponse response = doFilter(request);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("La cabecera X-Session-Id debe ser un UUID válido");
        verify(chain, never()).doFilter(any(), any());
        verifyNoInteractions(sessionService, sessionContext);
    }

    /**
     * Admin routes carry a bearer token instead of a session, so they must not be rejected here
     * for a missing {@code X-Session-Id}.
     */
    @ParameterizedTest
    @CsvSource({
            "GET,    /actuator/health",
            "GET,    /api/v1/market-rates",
            "POST,   /api/v1/admin/knowledge/ingest",
            "DELETE, /api/v1/admin/knowledge",
            "DELETE, /api/v1/market-rates"
    })
    void shouldSkipFilterWhenRouteNeedsNoSession(String method, String uri) throws Exception {
        MockHttpServletResponse response = doFilter(new MockHttpServletRequest(method, uri));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
        verifyNoInteractions(sessionService, sessionContext);
    }

    private MockHttpServletResponse doFilter(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}