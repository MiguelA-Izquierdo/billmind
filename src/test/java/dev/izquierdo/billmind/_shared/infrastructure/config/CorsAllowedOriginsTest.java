package dev.izquierdo.billmind._shared.infrastructure.config;

import dev.izquierdo.billmind._shared.application.command.CommandBus;
import dev.izquierdo.billmind._shared.application.query.QueryBus;
import dev.izquierdo.billmind._shared.domain.port.ExternalAuthPort;
import dev.izquierdo.billmind._shared.infrastructure.auth.ApiSecurityErrorHandler;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.RateLimiter;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitPolicyResolver;
import dev.izquierdo.billmind._shared.infrastructure.route.RequestPathMatcher;
import dev.izquierdo.billmind._shared.infrastructure.route.RouteAccessAuthorizationManager;
import dev.izquierdo.billmind._shared.infrastructure.route.RouteAccessPolicy;
import dev.izquierdo.billmind._shared.infrastructure.session.SessionContext;
import dev.izquierdo.billmind._shared.infrastructure.session.SessionService;
import dev.izquierdo.billmind.market.infrastructure.controller.ElectricityRateController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code cors.allowed.origins} is documented as a comma-separated list, so every origin in it must be
 * matched on its own. A single-value reading passes the whole line to the browser as one origin and
 * silently rejects all of them, including the first.
 */
@WebMvcTest(ElectricityRateController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, RequestPathMatcher.class, RouteAccessPolicy.class,
        RouteAccessAuthorizationManager.class, ApiSecurityErrorHandler.class, RateLimitPolicyResolver.class})
@TestPropertySource(properties = "cors.allowed.origins=http://localhost:8080, http://localhost:8082")
class CorsAllowedOriginsTest {

    private static final String ADMIN_RATES = "/api/v1/admin/market-rates";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommandBus commandBus;

    @MockitoBean
    private QueryBus queryBus;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private SessionContext sessionContext;

    @MockitoBean
    private ExternalAuthPort externalAuthPort;

    @MockitoBean
    private RateLimiter rateLimiter;

    @Test
    void shouldAllowTheFirstConfiguredOrigin() throws Exception {
        mockMvc.perform(preflightFrom("http://localhost:8080"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8080"));
    }

    @Test
    void shouldAllowASubsequentOriginEvenWhenWrittenWithSurroundingSpace() throws Exception {
        mockMvc.perform(preflightFrom("http://localhost:8082"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8082"));
    }

    @Test
    void shouldRejectAnOriginThatIsNotConfigured() throws Exception {
        mockMvc.perform(preflightFrom("http://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    private MockHttpServletRequestBuilder preflightFrom(String origin) {
        return options(ADMIN_RATES)
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "GET");
    }
}