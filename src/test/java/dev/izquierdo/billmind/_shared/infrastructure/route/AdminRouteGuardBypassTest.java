package dev.izquierdo.billmind._shared.infrastructure.route;

import dev.izquierdo.billmind._shared.application.command.CommandBus;
import dev.izquierdo.billmind._shared.application.query.QueryBus;
import dev.izquierdo.billmind._shared.domain.port.ExternalAuthPort;
import dev.izquierdo.billmind._shared.infrastructure.auth.ApiSecurityErrorHandler;
import dev.izquierdo.billmind._shared.infrastructure.config.SecurityConfig;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.RateLimiter;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.model.RateLimitVerdict;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitPolicyResolver;
import dev.izquierdo.billmind._shared.infrastructure.session.SessionContext;
import dev.izquierdo.billmind._shared.infrastructure.session.SessionService;
import dev.izquierdo.billmind.knowledge.domain.port.KnowledgeRepository;
import dev.izquierdo.billmind.knowledge.infrastructure.controller.KnowledgeAdminController;
import dev.izquierdo.billmind.market.infrastructure.controller.ElectricityRateController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves that admin routes are guarded on the same path the {@code DispatcherServlet} routes on.
 *
 * <p>{@code RouteAccessPolicy} classifies on {@code request.getRequestURI()} — the raw, percent-encoded
 * URI — while the dispatcher routes on the decoded {@code RequestPath}. When the two disagree, an admin
 * request slips past the guard as anonymous yet still reaches its admin controller. These tests send an
 * untokened request whose <em>raw</em> URI hides its admin identity but whose <em>routed</em> path is
 * admin; a guarded app answers {@code 401}. The two {@code bypass} cases fail until the guard matches on
 * the routed path.
 *
 * <p>Percent-encoding of an ordinary letter ({@code %61}→{@code a}) is used because it passes Spring
 * Security's {@code StrictHttpFirewall}. The matrix-parameter vector ({@code /market-rates;x=1}) is a
 * non-issue: the firewall rejects the semicolon with a {@code 400} before the guard ever runs.
 */
@WebMvcTest({KnowledgeAdminController.class, ElectricityRateController.class})
@Import({SecurityConfig.class, RequestPathMatcher.class, RouteAccessPolicy.class,
        RouteAccessAuthorizationManager.class, ApiSecurityErrorHandler.class, RateLimitPolicyResolver.class})
class AdminRouteGuardBypassTest {

    private static final String SESSION_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommandBus commandBus;

    @MockitoBean
    private QueryBus queryBus;

    @MockitoBean
    private KnowledgeRepository knowledgeRepository;

    @MockitoBean
    private ExternalAuthPort externalAuthPort;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private SessionContext sessionContext;

    @MockitoBean
    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        when(rateLimiter.check(any(), any(), any())).thenReturn(RateLimitVerdict.unlimited());
        when(queryBus.dispatch(any())).thenReturn(List.of());
    }

    // --- Controls: the guard works when raw and routed paths agree ---

    @Test
    void shouldRejectPlainAdminSearchWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/knowledge/search").param("q", "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectPlainMarketRatesDeleteWithoutToken() throws Exception {
        mockMvc.perform(delete("/api/v1/market-rates"))
                .andExpect(status().isUnauthorized());
    }

    // --- Bypasses: raw URI hides the admin identity the dispatcher still routes on ---

    @Test
    void shouldRejectPercentEncodedAdminSearchWithoutToken() throws Exception {
        mockMvc.perform(get("/placeholder")
                        .with(rawUri("/api/v1/%61dmin/knowledge/search"))
                        .param("q", "x")
                        .header("X-Session-Id", SESSION_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectPercentEncodedMarketRatesDeleteWithoutToken() throws Exception {
        mockMvc.perform(delete("/placeholder")
                        .with(rawUri("/api/v1/market-rate%73"))
                        .header("X-Session-Id", SESSION_ID))
                .andExpect(status().isUnauthorized());
    }

    /** Forces the raw request URI the guard reads, exactly as a real container would present it. */
    private static RequestPostProcessor rawUri(String uri) {
        return request -> {
            request.setRequestURI(uri);
            return request;
        };
    }
}
