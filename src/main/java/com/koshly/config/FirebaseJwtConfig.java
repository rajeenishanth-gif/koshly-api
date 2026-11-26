package com.koshly.config;

import java.time.Instant;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class FirebaseJwtConfig {

    // Google's public keys for Firebase ID tokens
    static final String JWKS_URL =
            "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

    @Bean
    JwtDecoder jwtDecoder(KoshlyProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(JWKS_URL).build();
        decoder.setJwtValidator(firebaseValidator(properties.firebaseProjectId()));
        return decoder;
    }

    // Not private, so the tests in Step 8 can use it
    static OAuth2TokenValidator<Jwt> firebaseValidator(String projectId) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("https://securetoken.google.com/" + projectId),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(projectId)),
                new JwtClaimValidator<String>("sub", sub -> sub != null && !sub.isBlank() && sub.length() <= 128),
                // Sign-in time must be in the past (60 seconds allowed for clock differences)
                new JwtClaimValidator<Object>("auth_time", t -> t instanceof Number seconds
                        && seconds.longValue() <= Instant.now().getEpochSecond() + 60));
    }
}