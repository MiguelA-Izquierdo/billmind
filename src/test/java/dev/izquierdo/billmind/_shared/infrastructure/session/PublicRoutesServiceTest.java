package dev.izquierdo.billmind._shared.infrastructure.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class PublicRoutesServiceTest {

    private PublicRoutesService publicRoutesService;

    @BeforeEach
    void setUp() {
        publicRoutesService = new PublicRoutesService();
    }

    @Test
    void shouldAllowGetMarketRates() {
        assertThat(publicRoutesService.isPublicRoute(request("GET", "/api/v1/market-rates"))).isTrue();
    }

    @Test
    void shouldAllowDeleteMarketRates() {
        assertThat(publicRoutesService.isPublicRoute(request("DELETE", "/api/v1/market-rates"))).isTrue();
    }

    @Test
    void shouldDenyPostMarketRates() {
        assertThat(publicRoutesService.isPublicRoute(request("POST", "/api/v1/market-rates"))).isFalse();
    }

    @Test
    void shouldDenyPutMarketRates() {
        assertThat(publicRoutesService.isPublicRoute(request("PUT", "/api/v1/market-rates"))).isFalse();
    }

    @Test
    void shouldDenyGetInvoices() {
        assertThat(publicRoutesService.isPublicRoute(request("GET", "/api/v1/invoices"))).isFalse();
    }

    @Test
    void shouldDenyPostInvoices() {
        assertThat(publicRoutesService.isPublicRoute(request("POST", "/api/v1/invoices"))).isFalse();
    }

    @Test
    void shouldDenyActuatorPaths() {
        assertThat(publicRoutesService.isPublicRoute(request("GET", "/actuator/health"))).isFalse();
    }

    private MockHttpServletRequest request(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }
}