package com.koshly.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

class FirebaseJwtValidatorTest {

    private static final String PROJECT = "koshly-dev";
    private final OAuth2TokenValidator<Jwt> validator = FirebaseJwtConfig.firebaseValidator(PROJECT);

    // A token that should pass; each test changes one thing about it
    private static Jwt token(Consumer<Jwt.Builder> change) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuer("https://securetoken.google.com/" + PROJECT)
                .audience(List.of(PROJECT))
                .subject("user-123")
                .issuedAt(now.minusSeconds(60))
                .expiresAt(now.plusSeconds(3600));
        change.accept(builder);
        return builder.build();
    }

    @Test
    void acceptsAValidToken() {
        assertThat(validator.validate(token(b -> { })).hasErrors()).isFalse();
    }

    @Test
    void rejectsTokenFromAnotherFirebaseProject() {
        assertThat(validator.validate(token(b -> b.audience(List.of("someone-else")))).hasErrors()).isTrue();
    }

    @Test
    void rejectsWrongIssuer() {
        assertThat(validator.validate(token(b -> b.issuer("https://evil.example.com"))).hasErrors()).isTrue();
    }

    @Test
    void rejectsExpiredToken() {
        Instant past = Instant.now().minusSeconds(7200);
        assertThat(validator.validate(token(b -> b.issuedAt(past).expiresAt(past.plusSeconds(3600))))
                .hasErrors()).isTrue();
    }

    @Test
    void rejectsTokenWithoutUserId() {
        assertThat(validator.validate(token(b -> b.subject(" "))).hasErrors()).isTrue();
    }
}