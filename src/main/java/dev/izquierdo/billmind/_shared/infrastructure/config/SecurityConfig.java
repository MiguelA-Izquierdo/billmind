package dev.izquierdo.billmind._shared.infrastructure.config;

import dev.izquierdo.billmind._shared.infrastructure.auth.ApiSecurityErrorHandler;
import dev.izquierdo.billmind._shared.infrastructure.auth.JwtAuthFilter;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.PostAuthRateLimitFilter;
import dev.izquierdo.billmind._shared.infrastructure.ratelimit.RateLimitFilter;
import dev.izquierdo.billmind._shared.infrastructure.route.RouteAccessAuthorizationManager;
import dev.izquierdo.billmind._shared.infrastructure.session.SessionFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Authentication and authorization are separate concerns here, and deliberately so. {@code JwtAuthFilter}
 * only establishes an identity; the decision is taken by the authorization engine below and, one layer
 * deeper, by the {@code @PreAuthorize} on each admin handler. A route is never open merely because a
 * filter chose not to run.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final SessionFilter sessionFilter;
    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final PostAuthRateLimitFilter postAuthRateLimitFilter;
    private final RouteAccessAuthorizationManager routeAccessAuthorizationManager;
    private final ApiSecurityErrorHandler apiSecurityErrorHandler;

    @Value("${cors.allowed.origin}")
    private String allowedOrigin;

    public SecurityConfig(SessionFilter sessionFilter, JwtAuthFilter jwtAuthFilter,
                          RateLimitFilter rateLimitFilter, PostAuthRateLimitFilter postAuthRateLimitFilter,
                          RouteAccessAuthorizationManager routeAccessAuthorizationManager,
                          ApiSecurityErrorHandler apiSecurityErrorHandler) {
        this.sessionFilter = sessionFilter;
        this.jwtAuthFilter = jwtAuthFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.postAuthRateLimitFilter = postAuthRateLimitFilter;
        this.routeAccessAuthorizationManager = routeAccessAuthorizationManager;
        this.apiSecurityErrorHandler = apiSecurityErrorHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().access(routeAccessAuthorizationManager))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(apiSecurityErrorHandler)
                        .accessDeniedHandler(apiSecurityErrorHandler))
                // Runs as: RateLimitFilter → JwtAuthFilter → PostAuthRateLimitFilter → SessionFilter.
                // One descending addFilterBefore chain, never mixed with addFilterAfter: HttpSecurity
                // assigns order(anchor) ± 1, so anchoring two filters on the same one from opposite
                // sides collides and the tie falls to insertion order. SecurityFilterChainOrderTest pins it.
                .addFilterBefore(sessionFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(postAuthRateLimitFilter, SessionFilter.class)
                .addFilterBefore(jwtAuthFilter, PostAuthRateLimitFilter.class)
                .addFilterBefore(rateLimitFilter, JwtAuthFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}