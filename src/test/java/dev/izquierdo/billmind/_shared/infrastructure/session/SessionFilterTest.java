package dev.izquierdo.billmind._shared.infrastructure.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SessionFilterTest {

    private SessionService sessionService;
    private SessionContext sessionContext;
    private PublicRoutesService publicRoutesService;
    private SessionFilter filter;

    @BeforeEach
    void setUp() {
        sessionService = mock(SessionService.class);
        sessionContext = mock(SessionContext.class);
        publicRoutesService = mock(PublicRoutesService.class);
        filter = new SessionFilter(sessionService, sessionContext, new ObjectMapper(), publicRoutesService);
    }

    @Test
    void shouldPassThroughAndUpsertSessionWhenHeaderIsValid() throws Exception {
        UUID sessionId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/invoices");
        request.addHeader("X-Session-Id", sessionId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(sessionService).upsert(sessionId);
        verify(sessionContext).setSessionId(sessionId);
        verify(chain).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldRejectWithMissingHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/invoices/upload");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("La cabecera X-Session-Id es obligatoria");
        verify(chain, never()).doFilter(any(), any());
        verifyNoInteractions(sessionService, sessionContext);
    }

    @Test
    void shouldRejectWithInvalidUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/invoices");
        request.addHeader("X-Session-Id", "not-a-uuid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("La cabecera X-Session-Id debe ser un UUID válido");
        verify(chain, never()).doFilter(any(), any());
        verifyNoInteractions(sessionService, sessionContext);
    }

    @Test
    void shouldSkipFilterForNonApiPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        verifyNoInteractions(sessionService, sessionContext);
    }

    @Test
    void shouldSkipFilterForPublicApiRoute() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/market-rates");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(publicRoutesService.isPublicRoute(request)).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        verifyNoInteractions(sessionService, sessionContext);
    }
}