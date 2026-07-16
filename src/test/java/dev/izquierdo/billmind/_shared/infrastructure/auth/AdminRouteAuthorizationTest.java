package dev.izquierdo.billmind._shared.infrastructure.auth;

import dev.izquierdo.billmind._shared.application.command.CommandBus;
import dev.izquierdo.billmind._shared.application.query.QueryBus;
import dev.izquierdo.billmind._shared.domain.port.ExternalAuthPort;
import dev.izquierdo.billmind._shared.infrastructure.config.SecurityConfig;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.RateLimiter;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.model.RateLimitVerdict;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitPolicyResolver;
import dev.izquierdo.billmind._shared.infrastructure.route.RequestPathMatcher;
import dev.izquierdo.billmind._shared.infrastructure.route.RouteAccess;
import dev.izquierdo.billmind._shared.infrastructure.route.RouteAccessAuthorizationManager;
import dev.izquierdo.billmind._shared.infrastructure.route.RouteAccessPolicy;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The access decision on an admin route, end to end through the real filter chain.
 *
 * <p>{@code JwtAuthFilter} no longer answers anything: it authenticates, and the decision is taken by
 * the authorization engine ({@code RouteAccessAuthorizationManager}) with the {@code @PreAuthorize} on
 * each admin handler behind it. The last test is the point of the whole arrangement — it hands the
 * engine a <em>broken</em> {@code RouteAccessPolicy} that swears the admin route is anonymous, exactly
 * the failure the guard could not survive when {@code anyRequest().permitAll()} left the engine idle,
 * and the handler-level guard still denies it.
 */
@WebMvcTest({KnowledgeAdminController.class, ElectricityRateController.class})
@Import({SecurityConfig.class, RequestPathMatcher.class, RouteAccessAuthorizationManager.class,
        ApiSecurityErrorHandler.class, RateLimitPolicyResolver.class})
class AdminRouteAuthorizationTest {

    private static final String ADMIN_SEARCH = "/api/v1/admin/knowledge/search";
    private static final String VALID_TOKEN = "Bearer valid-token";
    private static final String SESSION_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

    @Autowired
    private MockMvc mockMvc;

    /** Mocked, not imported: the last test needs a policy that lies about an admin route. */
    @MockitoBean
    private RouteAccessPolicy routeAccessPolicy;

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
        when(routeAccessPolicy.accessFor(any())).thenReturn(RouteAccess.ADMIN);
    }

    @Test
    void shouldAnswerUnauthorizedWhenNoTokenIsPresent() throws Exception {
        mockMvc.perform(get(ADMIN_SEARCH).param("q", "x"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Se requiere autenticación para realizar esta operación"));

        verify(queryBus, never()).dispatch(any());
    }

    @Test
    void shouldAnswerForbiddenWhenIntrospectionRejectsTheToken() throws Exception {
        when(externalAuthPort.isAuthorized(VALID_TOKEN)).thenReturn(false);

        mockMvc.perform(get(ADMIN_SEARCH).param("q", "x").header("Authorization", VALID_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No tienes permisos para realizar esta operación"));

        verify(queryBus, never()).dispatch(any());
    }

    @Test
    void shouldServeTheAdminRouteWhenIntrospectionAcceptsTheToken() throws Exception {
        when(externalAuthPort.isAuthorized(VALID_TOKEN)).thenReturn(true);

        mockMvc.perform(get(ADMIN_SEARCH).param("q", "x").header("Authorization", VALID_TOKEN))
                .andExpect(status().isOk());

        verify(queryBus).dispatch(any());
    }

    @Test
    void shouldGuardTheMarketRatesDeleteWhichLivesOutsideTheAdminTree() throws Exception {
        mockMvc.perform(delete("/api/v1/market-rates"))
                .andExpect(status().isUnauthorized());

        verify(commandBus, never()).dispatch(any());
    }

    /**
     * Defense in depth: the route policy — the single input the engine and every filter share — is made
     * to misclassify the admin route as anonymous. Nothing before the dispatcher objects, and the
     * {@code @PreAuthorize} on the handler is the only thing left standing.
     */
    @Test
    void shouldStillDenyTheAdminHandlerWhenTheRoutePolicyMisclassifiesTheRoute() throws Exception {
        when(routeAccessPolicy.accessFor(any())).thenReturn(RouteAccess.ANONYMOUS);

        mockMvc.perform(get(ADMIN_SEARCH).param("q", "x").header("X-Session-Id", SESSION_ID))
                .andExpect(status().isUnauthorized());

        verify(queryBus, never()).dispatch(any());
    }
}