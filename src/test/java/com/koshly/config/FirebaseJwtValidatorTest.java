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
    private static final String ISSUER = "https://securetoken.google.com/" + PROJECT;
    private final OAuth2TokenValidator<Jwt> validator = FirebaseJwtConfig.firebaseValidator(ISSUER, PROJECT);

    // A token that should pass; each test changes one thing about it
    private static Jwt token(Consumer<Jwt.Builder> change) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .audience(List.of(PROJECT))
                .subject("user-123")
                .issuedAt(now.minusSeconds(60))
                .expiresAt(now.plusSeconds(3600))
                .claim("auth_time", now.minusSeconds(120).getEpochSecond());
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
    void rejectsSignInTimeInTheFuture() {
        long future = Instant.now().plusSeconds(3600).getEpochSecond();
        assertThat(validator.validate(token(b -> b.claim("auth_time", future))).hasErrors()).isTrue();
    }

    @Test
    void rejectsTokenWithoutUserId() {
        assertThat(validator.validate(token(b -> b.subject(" "))).hasErrors()).isTrue();
    }
}