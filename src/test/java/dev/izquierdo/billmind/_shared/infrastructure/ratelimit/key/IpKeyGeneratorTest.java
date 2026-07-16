package dev.izquierdo.billmind._shared.infrastructure.ratelimit.key;

import dev.izquierdo.billmind._shared.infrastructure.ratelimit.policy.KeyType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class IpKeyGeneratorTest {

    @Test
    void shouldKeyByRemoteAddrByDefault() {
        IpKeyGenerator generator = new IpKeyGenerator(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("X-Forwarded-For", "1.2.3.4");

        assertThat(generator.identity(request)).contains("ip:203.0.113.7");
    }

    @Test
    void shouldHonourForwardedForWhenTrusted() {
        IpKeyGenerator generator = new IpKeyGenerator(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 10.0.0.1");

        assertThat(generator.identity(request)).contains("ip:1.2.3.4");
    }

    @Test
    void shouldFallBackToRemoteAddrWhenTrustedButHeaderAbsent() {
        IpKeyGenerator generator = new IpKeyGenerator(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");

        assertThat(generator.identity(request)).contains("ip:10.0.0.1");
    }

    @Test
    void shouldReportTypeIp() {
        assertThat(new IpKeyGenerator(false).type()).isEqualTo(KeyType.IP);
    }
}