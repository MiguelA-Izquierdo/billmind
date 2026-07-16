package dev.izquierdo.billmind._shared.infrastructure.ratelimit.key;

import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.KeyType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class SessionKeyGeneratorTest {

    private static final String SESSION = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";

    private final SessionKeyGenerator generator = new SessionKeyGenerator();

    @Test
    void shouldKeyBySessionHeader() {
        assertThat(generator.identity(withSession(SESSION))).contains("sess:" + SESSION);
    }

    /** The same session in upper case must not get a second bucket. */
    @Test
    void shouldCanonicaliseTheSessionSoOneVisitorGetsOneBucket() {
        assertThat(generator.identity(withSession(SESSION.toUpperCase())))
                .contains("sess:" + SESSION);
    }

    @Test
    void shouldIgnoreSurroundingWhitespace() {
        assertThat(generator.identity(withSession("  " + SESSION + "  "))).contains("sess:" + SESSION);
    }

    /** Not a session: the layer is skipped and the IP ceiling is what bounds the caller. */
    @Test
    void shouldReturnEmptyWhenSessionHeaderIsNotAUuid() {
        assertThat(generator.identity(withSession("a1b2c3"))).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenSessionHeaderAbsent() {
        assertThat(generator.identity(new MockHttpServletRequest())).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenSessionHeaderBlank() {
        assertThat(generator.identity(withSession("  "))).isEmpty();
    }

    @Test
    void shouldReportTypeSession() {
        assertThat(generator.type()).isEqualTo(KeyType.SESSION);
    }

    private static MockHttpServletRequest withSession(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Session-Id", value);
        return request;
    }
}