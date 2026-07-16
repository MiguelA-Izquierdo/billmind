package dev.izquierdo.billmind._shared.infrastructure.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalTokenAuthenticationTest {

    private static final String RAW_TOKEN = "super-secret-token";

    @Test
    void shouldAuthenticateAndGrantAdminWhenIntrospectionReportedAnAdminRole() {
        ExternalTokenAuthentication token = ExternalTokenAuthentication.authorized(RAW_TOKEN);

        assertThat(token.isAuthenticated()).isTrue();
        assertThat(token.isAdmin()).isTrue();
        assertThat(token.getAuthorities()).extracting("authority").containsExactly(Roles.ADMIN);
    }

    /** The invariant this class exists to hold: a refused token is not an authenticated caller. */
    @Test
    void shouldNotAuthenticateARejectedToken() {
        ExternalTokenAuthentication token = ExternalTokenAuthentication.rejected(RAW_TOKEN);

        assertThat(token.isAuthenticated()).isFalse();
        assertThat(token.isAdmin()).isFalse();
        assertThat(token.getAuthorities()).isEmpty();
    }

    /**
     * Why the flag matters, stated as the failure it prevents: Spring's authority checks gate on
     * {@code isAuthenticated()} before ever looking at the authorities. With the old
     * {@code setAuthenticated(true)}, a rule as ordinary as {@code hasRole('USER')} — or
     * {@code authenticated()} — would have been one annotation away from admitting a token the
     * identity provider had refused.
     */
    @Test
    void shouldBeDeniedByAnAuthorityCheckThatOnlyRequiresBeingAuthenticated() {
        AuthorizationManager<Object> anyAuthenticated = AuthorityAuthorizationManager.hasRole("USER");
        Authentication rejected = ExternalTokenAuthentication.rejected(RAW_TOKEN);

        assertThat(anyAuthenticated.check(() -> rejected, new Object()).isGranted()).isFalse();
    }

    /** Rule #6: the credential never survives, not even as the principal. */
    @Test
    void shouldExposeAFingerprintAsPrincipalNeverTheRawToken() {
        Authentication token = ExternalTokenAuthentication.authorized(RAW_TOKEN);

        assertThat(token.getPrincipal().toString())
                .isNotEmpty()
                .doesNotContain(RAW_TOKEN);
        assertThat(token.getCredentials()).isNull();
    }

    @Test
    void shouldFingerprintTheSameTokenToTheSameIdentity() {
        assertThat(ExternalTokenAuthentication.authorized(RAW_TOKEN).getPrincipal())
                .isEqualTo(ExternalTokenAuthentication.authorized(RAW_TOKEN).getPrincipal())
                .isNotEqualTo(ExternalTokenAuthentication.authorized("another-token").getPrincipal());
    }
}