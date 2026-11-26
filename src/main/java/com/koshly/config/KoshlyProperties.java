package com.koshly.config;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings under "koshly." in application.properties. Checked at startup:
 * the app refuses to start if one is missing or not https.
 */
@Validated
@ConfigurationProperties(prefix = "koshly")
public record KoshlyProperties(
        /** Which data region this deployment serves, e.g. "us" (later also "in"). */
        @NotBlank @Pattern(regexp = "[a-z]{2,10}") String region,
        @NotNull @Valid Firebase firebase,
        @NotNull @Valid Cors cors,
        @NotNull @Valid Retention retention) {

    /** Where Firebase login tokens come from and how to check them. */
    public record Firebase(
            @NotBlank String projectId,
            @NotBlank @Pattern(regexp = "https://.+", message = "must be an https:// URL") String issuerUri,
            @NotBlank @Pattern(regexp = "https://.+", message = "must be an https:// URL") String jwkSetUri) {
    }

    public record Cors(@NotNull List<String> allowedOrigins) {
    }

    /**
     * How long removed accounts are kept (for Undo) before being deleted for good,
     * and when the clean-up runs (a Spring cron expression, UTC).
     */
    public record Retention(@Min(1) int removedAccountDays, @NotBlank String cleanupCron) {
    }
}
