package com.koshly.config;

import java.time.Duration;
import java.util.List;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Let Spring's own error handling through, so errors are reported properly
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        // Signed in is enough to ask "who am I / am I verified yet?"
                        .requestMatchers(HttpMethod.GET, "/api/users/current").authenticated()
                        // Everything else under /api needs a verified email (403 if not)
                        .requestMatchers("/api/**").access(verifiedEmail())
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults());
        return http.build();
    }

    // Allows the request only if the account is confirmed: its email is verified, or
    // it has a phone number (Koshly only sets one after the user confirms a code
    // sent to it, see AccountLinkService; Firebase phone sign-in verifies it too).
    private static AuthorizationManager<RequestAuthorizationContext> verifiedEmail() {
        return (authentication, context) -> {
            Authentication auth = authentication.get();
            boolean verified = auth instanceof JwtAuthenticationToken token
                    && (Boolean.TRUE.equals(token.getToken().getClaimAsBoolean("email_verified"))
                            || token.getToken().getClaimAsString("phone_number") != null);
            return new AuthorizationDecision(verified);
        };
    }

    // Lets the web version of the app (http://localhost:8081) call the API from a browser
    @Bean
    CorsConfigurationSource corsConfigurationSource(KoshlyProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.cors().allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}