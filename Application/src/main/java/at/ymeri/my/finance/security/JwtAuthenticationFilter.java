package at.ymeri.my.finance.security;

import at.ymeri.my.finance.domain.api.ValidateSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Reads {@code Authorization: Bearer <token>}, decodes it via {@link JwtTokenService}, and —
 * only if {@link ValidateSessionService} also confirms the token's version is still current
 * (research.md R2) — marks the request authenticated. Any failure at any step (missing header,
 * bad token, stale version) simply leaves the request unauthenticated; Spring Security's own
 * entry point turns that into a 401 for any endpoint that isn't {@code permitAll} (SecurityConfig).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final ValidateSessionService validateSessionService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, ValidateSessionService validateSessionService) {
        this.jwtTokenService = jwtTokenService;
        this.validateSessionService = validateSessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        extractToken(request)
                .flatMap(jwtTokenService::parse)
                .filter(claims -> validateSessionService.isValid(claims.username(), claims.tokenVersion()))
                .ifPresent(claims -> {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(claims.username(), null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
        filterChain.doFilter(request, response);
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return Optional.of(header.substring("Bearer ".length()));
        }
        return Optional.empty();
    }
}
