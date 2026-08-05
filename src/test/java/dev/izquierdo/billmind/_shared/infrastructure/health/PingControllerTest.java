package dev.izquierdo.billmind._shared.infrastructure.health;

import dev.izquierdo.billmind._shared.domain.port.ExternalAuthPort;
import dev.izquierdo.billmind._shared.infrastructure.auth.ApiSecurityErrorHandler;
import dev.izquierdo.billmind._shared.infrastructure.config.SecurityConfig;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.RateLimiter;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.RateLimitPolicyResolver;
import dev.izquierdo.billmind._shared.infrastructure.route.RequestPathMatcher;
import dev.izquierdo.billmind._shared.infrastructure.route.RouteAccessAuthorizationManager;
import dev.izquierdo.billmind._shared.infrastructure.route.RouteAccessPolicy;
import dev.izquierdo.billmind._shared.infrastructure.session.SessionContext;
import dev.izquierdo.billmind._shared.infrastructure.session.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole point of this endpoint is that it answers a caller carrying nothing — no session header,
 * no token — so the full security chain is imported rather than disabled.
 */
@WebMvcTest(PingController.class)
@Import({SecurityConfig.class, RequestPathMatcher.class, RouteAccessPolicy.class,
        RouteAccessAuthorizationManager.class, ApiSecurityErrorHandler.class, RateLimitPolicyResolver.class})
class PingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DependencyHealthProbe dependencyHealthProbe;

    // Collaborators of the security filters, which are Filter beans and so live in the web slice.
    @MockitoBean
    private ExternalAuthPort externalAuthPort;

    @MockitoBean
    private RateLimiter rateLimiter;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private SessionContext sessionContext;

    @Test
    void shouldAnswer200WithAnEmptyBodyWhenDependenciesAreUp() throws Exception {
        when(dependencyHealthProbe.dependenciesUp()).thenReturn(true);

        String body = mockMvc.perform(get("/ping"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isEmpty();
    }

    @Test
    void shouldAnswer503WithAnEmptyBodyWhenADependencyIsDown() throws Exception {
        when(dependencyHealthProbe.dependenciesUp()).thenReturn(false);

        String body = mockMvc.perform(get("/ping"))
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isEmpty();
    }

    /** No X-Session-Id, no token: a monitor sends neither, and must still be answered. */
    @Test
    void shouldNotRequireASessionHeader() throws Exception {
        when(dependencyHealthProbe.dependenciesUp()).thenReturn(true);

        mockMvc.perform(get("/ping")).andExpect(status().isOk());

        verify(sessionService, never()).upsert(any());
    }

    /** A proxy caching the 200 would keep reporting health after the dependency went away. */
    @Test
    void shouldForbidCachingOfTheAnswer() throws Exception {
        when(dependencyHealthProbe.dependenciesUp()).thenReturn(true);

        mockMvc.perform(get("/ping"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}