package dev.izquierdo.billmind._shared.infrastructure.config;

import dev.izquierdo.billmind._shared.application.command.CommandBus;
import dev.izquierdo.billmind._shared.application.query.QueryBus;
import dev.izquierdo.billmind._shared.domain.port.ExternalAuthPort;
import dev.izquierdo.billmind._shared.infrastructure.auth.JwtAuthFilter;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.PostAuthRateLimitFilter;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.RateLimitFilter;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.RateLimiter;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitPolicyResolver;
import dev.izquierdo.billmind._shared.infrastructure.auth.ApiSecurityErrorHandler;
import dev.izquierdo.billmind._shared.infrastructure.route.RequestPathMatcher;
import dev.izquierdo.billmind._shared.infrastructure.route.RouteAccessAuthorizationManager;
import dev.izquierdo.billmind._shared.infrastructure.route.RouteAccessPolicy;
import dev.izquierdo.billmind._shared.infrastructure.session.SessionContext;
import dev.izquierdo.billmind._shared.infrastructure.session.SessionFilter;
import dev.izquierdo.billmind._shared.infrastructure.session.SessionService;
import dev.izquierdo.billmind.market.infrastructure.controller.ElectricityRateController;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the filter order as a contract. {@code HttpSecurity} derives each custom filter's order as
 * {@code order(anchor) ± 1}, so two filters anchored on the same one from opposite sides collide and
 * the tie is broken only by the stable sort's insertion order — which reorders silently if someone
 * moves a line in {@code SecurityConfig}. This test fails when that happens.
 */
@WebMvcTest(ElectricityRateController.class)
@Import({SecurityConfig.class, RequestPathMatcher.class, RouteAccessPolicy.class,
        RouteAccessAuthorizationManager.class, ApiSecurityErrorHandler.class, RateLimitPolicyResolver.class})
class SecurityFilterChainOrderTest {

    private static final List<Class<? extends Filter>> CUSTOM_FILTERS = List.of(
            RateLimitFilter.class, JwtAuthFilter.class, PostAuthRateLimitFilter.class, SessionFilter.class);

    @Autowired
    private SecurityFilterChain securityFilterChain;

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
    void shouldRunRateLimitBeforeAuthAndPostAuthLimitAfterIt() {
        List<Class<?>> actual = filterClasses().stream().filter(CUSTOM_FILTERS::contains).toList();

        assertThat(actual).containsExactly(
                RateLimitFilter.class, JwtAuthFilter.class, PostAuthRateLimitFilter.class, SessionFilter.class);
    }

    @Test
    void shouldRunEveryCustomFilterBeforeTheAuthorizationFilter() {
        List<Class<?>> chain = filterClasses();

        assertThat(chain).contains(AuthorizationFilter.class);
        CUSTOM_FILTERS.forEach(filter ->
                assertThat(chain.indexOf(filter)).isLessThan(chain.indexOf(AuthorizationFilter.class)));
    }

    private List<Class<?>> filterClasses() {
        return securityFilterChain.getFilters().stream().<Class<?>>map(Filter::getClass).toList();
    }
}