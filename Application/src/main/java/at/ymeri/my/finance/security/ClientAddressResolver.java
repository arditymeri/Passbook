package at.ymeri.my.finance.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Works out who is calling, for the purpose of counting their failed logins.
 *
 * <p><strong>Why the socket address is not the answer.</strong> In a deployment the backend is only
 * reachable through the reverse proxy that serves the frontend, so
 * {@code request.getRemoteAddr()} is that proxy on every single request. Keyed on it, per-caller
 * throttling collapses into one bucket for the whole internet — which is not a subtle failure, but
 * it is an invisible one: everything still compiles, and every test that does not go through a
 * proxy still passes.
 *
 * <p><strong>Why the rightmost entry, and not the first.</strong> {@code X-Forwarded-For} is
 * whatever the client sent, and anyone can send {@code X-Forwarded-For: 1.2.3.4}. Our proxy
 * <em>appends</em> the real peer to whatever arrived, so a spoofed request becomes
 * {@code "1.2.3.4, <real>"}: the last entry is the one our own infrastructure wrote, and every
 * entry before it is attacker-chosen. Taking the first — the natural-looking choice, and the one
 * most examples show — would hand an attacker a fresh counter on every request while appearing to
 * work perfectly in development, where nobody sends the header at all.
 *
 * <p><strong>What this gets wrong, stated rather than discovered.</strong> Behind two proxies, or
 * behind one that overwrites the header rather than appending to it, the rightmost entry is that
 * proxy and every caller shares a bucket. That degrades per-caller throttling to a single counter;
 * it opens nothing, and the instance-wide tier still applies.
 */
@Component
public class ClientAddressResolver {

    private static final String FORWARDED_FOR = "X-Forwarded-For";

    public String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            String nearest = hops[hops.length - 1].trim();
            if (!nearest.isEmpty()) {
                return nearest;
            }
        }
        String remote = request.getRemoteAddr();
        // A caller we cannot identify is counted as one caller rather than as nobody: an
        // unidentifiable request must not be a way around the per-caller tier.
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }
}
