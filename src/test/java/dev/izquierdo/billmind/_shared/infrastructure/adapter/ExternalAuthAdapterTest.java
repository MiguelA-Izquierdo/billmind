package dev.izquierdo.billmind._shared.infrastructure.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class ExternalAuthAdapterTest {

    private static final String BASE_URL = "http://auth.internal";
    private static final String INTROSPECT_URL = BASE_URL + "/introspect";
    private static final String TOKEN = "Bearer a-token";

    private MockRestServiceServer server;
    private ExternalAuthAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new ExternalAuthAdapter(builder, BASE_URL);
    }

    @Test
    void shouldAuthorizeWhenRolesContainSuperAdmin() {
        respondWith(withSuccess(body("""
                {"sub":"347ecfab-fce2-4738-997a-a19b741951b5",
                 "roles":["ROLE_USER","ROLE_SUPER_ADMIN"],
                 "isAdmin":true}
                """), MediaType.APPLICATION_JSON));

        assertThat(adapter.isAuthorized(TOKEN)).isTrue();
        server.verify();
    }

    @Test
    void shouldAuthorizeWhenRolesContainAdmin() {
        respondWith(withSuccess(body("""
                {"sub":"347ecfab","roles":["ROLE_ADMIN"],"isAdmin":true}
                """), MediaType.APPLICATION_JSON));

        assertThat(adapter.isAuthorized(TOKEN)).isTrue();
    }

    /** The token is perfectly valid — the auth service says so with a 200. It is still not an admin. */
    @Test
    void shouldNotAuthorizeValidTokenWithoutAnAdminRole() {
        respondWith(withSuccess(body("""
                {"sub":"347ecfab","roles":["ROLE_USER"],"isAdmin":false}
                """), MediaType.APPLICATION_JSON));

        assertThat(adapter.isAuthorized(TOKEN)).isFalse();
    }

    /** A forged "isAdmin" must not override the roles it contradicts. */
    @Test
    void shouldNotAuthorizeWhenIsAdminFlagContradictsRoles() {
        respondWith(withSuccess(body("""
                {"sub":"347ecfab","roles":["ROLE_USER"],"isAdmin":true}
                """), MediaType.APPLICATION_JSON));

        assertThat(adapter.isAuthorized(TOKEN)).isFalse();
    }

    @Test
    void shouldNotAuthorizeWhenRolesAreEmpty() {
        respondWith(withSuccess(body("""
                {"sub":"347ecfab","roles":[]}
                """), MediaType.APPLICATION_JSON));

        assertThat(adapter.isAuthorized(TOKEN)).isFalse();
    }

    @Test
    void shouldNotAuthorizeWhenBodyCarriesNoRoles() {
        respondWith(withSuccess(body("{}"), MediaType.APPLICATION_JSON));

        assertThat(adapter.isAuthorized(TOKEN)).isFalse();
    }

    @Test
    void shouldNotAuthorizeWhenResponseHasNoBody() {
        respondWith(withStatus(HttpStatus.NO_CONTENT));

        assertThat(adapter.isAuthorized(TOKEN)).isFalse();
    }

    @Test
    void shouldNotAuthorizeWhenTokenIsRejected() {
        respondWith(withUnauthorizedRequest());

        assertThat(adapter.isAuthorized(TOKEN)).isFalse();
    }

    /** Fail-closed: an auth service that is down never yields an admin. */
    @Test
    void shouldNotAuthorizeWhenAuthServiceFails() {
        respondWith(withServerError());

        assertThat(adapter.isAuthorized(TOKEN)).isFalse();
    }

    private void respondWith(org.springframework.test.web.client.ResponseCreator response) {
        server.expect(requestTo(INTROSPECT_URL))
                .andExpect(header("Authorization", TOKEN))
                .andRespond(response);
    }

    private static String body(String json) {
        return json.replace("\n", "");
    }
}