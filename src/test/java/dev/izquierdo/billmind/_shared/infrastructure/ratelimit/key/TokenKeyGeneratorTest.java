package dev.izquierdo.billmind._shared.infrastructure.ratelimit.key;

import dev.izquierdo.billmind._shared.infrastructure.auth.ExternalTokenAuthentication;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.KeyType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class TokenKeyGeneratorTest {

    private final TokenKeyGenerator generator = new TokenKeyGenerator();
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldKeyByTokenFingerprintNeverTheRawToken() {
        authenticate(ExternalTokenAuthentication.authorized("secret-token-value"));

        assertThat(generator.identity(request))
                .isPresent()
                .get(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .startsWith("tok:")
                .doesNotContain("secret-token-value");
    }

    @Test
    void shouldBeStableForTheSameToken() {
        authenticate(ExternalTokenAuthentication.authorized("same-token"));
        var first = generator.identity(request);
        authenticate(ExternalTokenAuthentication.authorized("same-token"));

        assertThat(generator.identity(request)).isEqualTo(first);
    }

    @Test
    void shouldDistinguishDifferentTokens() {
        authenticate(ExternalTokenAuthentication.authorized("token-a"));
        var first = generator.identity(request);
        authenticate(ExternalTokenAuthentication.authorized("token-b"));

        assertThat(generator.identity(request)).isNotEqualTo(first);
    }

    /**
     * The bucket store is keyed by this identity: were a rejected token to produce one, an anonymous
     * caller could mint an unbounded number of buckets by forging tokens.
     */
    @Test
    void shouldReturnEmptyWhenTokenWasRejectedByIntrospection() {
        authenticate(ExternalTokenAuthentication.rejected("forged-token"));

        assertThat(generator.identity(request)).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenNoAuthenticationIsPresent() {
        assertThat(generator.identity(request)).isEmpty();
    }

    /** A raw {@code Authorization} header on its own proves nothing — only the validated identity counts. */
    @Test
    void shouldIgnoreAnUnvalidatedAuthorizationHeader() {
        MockHttpServletRequest withHeader = new MockHttpServletRequest();
        withHeader.addHeader("Authorization", "Bearer forged-token");

        assertThat(generator.identity(withHeader)).isEmpty();
    }

    @Test
    void shouldReportTypeToken() {
        assertThat(generator.type()).isEqualTo(KeyType.TOKEN);
    }

    private void authenticate(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}