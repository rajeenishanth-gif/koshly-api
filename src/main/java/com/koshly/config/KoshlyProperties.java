package com.koshly.config;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "koshly")
public record KoshlyProperties(
        @NotBlank String firebaseProjectId,
        @NotNull Cors cors) {

    public record Cors(@NotNull List<String> allowedOrigins) {
    }
}