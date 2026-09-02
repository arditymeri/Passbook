package at.ymeri.my.finance.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless bearer-token API: no server-side session, no CSRF surface (CSRF exists to defend
 * cookie-based auth that browsers attach automatically; a bearer token in an explicit header is
 * never attached automatically, so there is nothing for CSRF protection to add here), no default
 * form-login/HTTP-Basic prompts. Public per research.md R5: {@code /auth/status}, {@code
 * /auth/setup}, {@code /auth/login}, and the existing Swagger UI/OpenAPI docs. Every other request
 * — every existing endpoint from every prior feature, no exceptions — requires
 * {@link JwtAuthenticationFilter} to have already marked it authenticated.
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/status", "/auth/setup", "/auth/login").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // Spring Boot's error handling resolves an unhandled exception (e.g. a
                        // failed @Valid bean-validation check with no local @ExceptionHandler,
                        // which Spring's DefaultHandlerExceptionResolver turns into
                        // response.sendError(400)) via an internal servlet-container forward to
                        // /error. JwtAuthenticationFilter, being a OncePerRequestFilter, skips
                        // that ERROR dispatch by default (its shouldNotFilterErrorDispatch()
                        // defaults to true), so no authentication is re-established for it — but
                        // Spring Security's own authorization filter DOES run on ERROR dispatches,
                        // and would otherwise reject this internal forward as unauthenticated,
                        // overwriting the original 400 with 401. Permitting /error is the
                        // standard fix (it reveals nothing beyond the status/body the original
                        // handler already decided to send).
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
