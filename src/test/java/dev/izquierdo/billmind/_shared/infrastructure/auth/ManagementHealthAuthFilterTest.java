package dev.izquierdo.billmind._shared.infrastructure.auth;

import dev.izquierdo.billmind._shared.domain.port.ExternalAuthPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ManagementHealthAuthFilterTest {

    private static final String ADMIN_TOKEN = "Bearer admin-token";

    private ExternalAuthPort externalAuthPort;
    private ManagementHealthAuthFilter filter;

    @BeforeEach
    void setUp() {
        externalAuthPort = mock(ExternalAuthPort.class);
        filter = new ManagementHealthAuthFilter(externalAuthPort);
    }

    @Test
    void shouldExposeAdminPrincipalWhenTokenIsAuthorized() throws Exception {
        when(externalAuthPort.isAuthorized(ADMIN_TOKEN)).thenReturn(true);
        MockHttpServletRequest request = actuatorRequest();
        request.addHeader("Authorization", ADMIN_TOKEN);

        HttpServletRequest forwarded = doFilterAndCaptureRequest(request);

        assertThat(forwarded.getUserPrincipal()).isNotNull();
        assertThat(forwarded.getUserPrincipal().getName()).isEqualTo("actuator-admin");
    }

    @Test
    void shouldNotExposePrincipalWhenTokenIsNotAuthorized() throws Exception {
        when(externalAuthPort.isAuthorized(ADMIN_TOKEN)).thenReturn(false);
        MockHttpServletRequest request = actuatorRequest();
        request.addHeader("Authorization", ADMIN_TOKEN);

        HttpServletRequest forwarded = doFilterAndCaptureRequest(request);

        assertThat(forwarded.getUserPrincipal()).isNull();
    }

    @Test
    void shouldNotCallAuthServiceWhenNoAuthorizationHeader() throws Exception {
        HttpServletRequest forwarded = doFilterAndCaptureRequest(actuatorRequest());

        assertThat(forwarded.getUserPrincipal()).isNull();
        verifyNoInteractions(externalAuthPort);
    }

    @Test
    void shouldIgnoreNonBearerAuthorizationHeader() throws Exception {
        MockHttpServletRequest request = actuatorRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        HttpServletRequest forwarded = doFilterAndCaptureRequest(request);

        assertThat(forwarded.getUserPrincipal()).isNull();
        verifyNoInteractions(externalAuthPort);
    }

    private static MockHttpServletRequest actuatorRequest() {
        return new MockHttpServletRequest("GET", "/actuator/health");
    }

    private HttpServletRequest doFilterAndCaptureRequest(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(chain).doFilter(captor.capture(), any());
        assertThat(response.getStatus()).isEqualTo(200);
        return captor.getValue();
    }
}