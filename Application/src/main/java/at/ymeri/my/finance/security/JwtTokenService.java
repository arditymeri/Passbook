package at.ymeri.my.finance.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and validates the session JWT — username + {@code tokenVersion} claims, a fixed expiry,
 * signature only (the "is this session still current" check against the account's live
 * {@code tokenVersion} is Domain's {@code ValidateSessionService}, not this class — research.md
 * R1/R2).
 *
 * <p>Signing key: {@code app.security.jwt-secret}, sourced from the {@code JWT_SECRET}
 * environment variable, normalized to a 256-bit key via SHA-256 so any non-empty string is
 * accepted. It is <strong>required</strong>: startup fails with an actionable message when it is
 * missing. Feature 021 removed the previous random-key fallback — it silently invalidated every
 * session on restart, and a built-in default is exactly what makes every install share a
 * credential (Self-Hosting Obligations: no credentials in version control; 021 research R8).
 */
@Service
public class JwtTokenService {

    private final String configuredSecret;
    private final Duration expiry;
    private SecretKey key;

    public JwtTokenService(@Value("${app.security.jwt-secret:}") String configuredSecret,
                            @Value("${app.security.jwt-expiry-hours:24}") long expiryHours) {
        this.configuredSecret = configuredSecret;
        this.expiry = Duration.ofHours(expiryHours);
    }

    @PostConstruct
    void init() {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is not set. Passbook signs session tokens with it, and there is "
                            + "deliberately no built-in default: a shipped fallback would mean "
                            + "every install shares a signing key, and a randomly generated one "
                            + "would log every session out on each restart. Copy .env.example to "
                            + ".env and set JWT_SECRET to a long random string, e.g. "
                            + "`openssl rand -base64 32`.");
        }
        key = Keys.hmacShaKeyFor(sha256(configuredSecret));
    }

    public record IssuedToken(String token, OffsetDateTime expiresAt) {
    }

    public record TokenClaims(String username, int tokenVersion) {
    }

    public IssuedToken issue(String username, int tokenVersion) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plus(expiry);
        String token = Jwts.builder()
                .subject(username)
                .claim("tokenVersion", tokenVersion)
                .issuedAt(Date.from(now.toInstant()))
                .expiration(Date.from(expiresAt.toInstant()))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    /**
     * Empty on any problem at all — bad signature, malformed token, or expired — never throws.
     */
    public Optional<TokenClaims> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            String username = claims.getSubject();
            Number tokenVersion = claims.get("tokenVersion", Number.class);
            if (username == null || tokenVersion == null) {
                return Optional.empty();
            }
            return Optional.of(new TokenClaims(username, tokenVersion.intValue()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
