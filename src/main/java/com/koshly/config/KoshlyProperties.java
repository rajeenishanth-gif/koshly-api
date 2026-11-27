package com.koshly.config;

import java.util.List;
import java.util.Map;

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
        @NotNull @Valid Retention retention,
        @NotNull @Valid Accounts accounts) {

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

    /**
     * Each Koshly account belongs to one country, decided by the Firebase tenant
     * it signed up in (one tenant per country, so the same email can have a US
     * account and a separate India account).
     *
     * @param tenants        country code -> Firebase tenant ID, e.g. IN -> "india-k3x9q".
     *                       Blank = that country has no tenant yet.
     * @param defaultCountry   country for sign-ins without a tenant (all of them until
     *                         tenants are set up). Blank = refuse sign-ins without a tenant.
     * @param enabledCountries countries open to users right now (launching with US only).
     *                         Others stay built but switched off until added here.
     */
    public record Accounts(Map<String, String> tenants, String defaultCountry, List<String> enabledCountries) {
    }
}
