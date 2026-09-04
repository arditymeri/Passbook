package at.ymeri.my.finance.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quickstart scenario 7. Small, and worth more than its size: the difference between the rightmost
 * and the leftmost entry of {@code X-Forwarded-For} is the difference between per-caller throttling
 * that works and per-caller throttling that an attacker resets on every request.
 */
class ClientAddressResolverTest {

    private final ClientAddressResolver resolver = new ClientAddressResolver();

    @Test
    void withNoProxyHeaderTheSocketAddressIsUsed() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.7");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void behindOneProxyTheAppendedPeerIsUsed() {
        // What a genuine request through our own proxy looks like.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For", "198.51.100.7");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void aSpoofedHeaderDoesNotWin() {
        // THE test. The attacker sent "1.2.3.4"; our proxy appended their real address after it.
        // Taking the first entry would give the attacker a brand-new counter per request, and this
        // is the only place that decision is visible.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 198.51.100.7");

        assertThat(resolver.resolve(request))
                .as("the rightmost entry is the one our own proxy wrote")
                .isEqualTo("198.51.100.7");
    }

    @Test
    void aLongSpoofedChainStillDoesNotWin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For", "1.1.1.1, 2.2.2.2, 3.3.3.3, 198.51.100.7");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void anEmptyHeaderFallsBackToTheSocketAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.7");
        request.addHeader("X-Forwarded-For", "   ");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void aCallerWithNoIdentityAtAllIsStillOneCaller() {
        // Never null and never empty: an unidentifiable request must not become a way around the
        // per-caller tier by having no key to count against.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(null);

        assertThat(resolver.resolve(request)).isNotBlank();
    }
}
