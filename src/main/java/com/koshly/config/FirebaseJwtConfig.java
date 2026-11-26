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

    // All addresses come from koshly.firebase.* in application.properties
    @Bean
    JwtDecoder jwtDecoder(KoshlyProperties properties) {
        KoshlyProperties.Firebase firebase = properties.firebase();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(firebase.jwkSetUri()).build();
        decoder.setJwtValidator(firebaseValidator(firebase.issuerUri(), firebase.projectId()));
        return decoder;
    }

    // Not private, so the tests in Step 8 can use it
    static OAuth2TokenValidator<Jwt> firebaseValidator(String issuerUri, String projectId) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(projectId)),
                new JwtClaimValidator<String>("sub", sub -> sub != null && !sub.isBlank() && sub.length() <= 128),
                // Sign-in time must be in the past (60 seconds allowed for clock differences)
                new JwtClaimValidator<Object>("auth_time", t -> t instanceof Number seconds
                        && seconds.longValue() <= Instant.now().getEpochSecond() + 60));
    }
}