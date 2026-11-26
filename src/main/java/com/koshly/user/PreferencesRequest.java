package com.koshly.user;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of PUT /api/users/current/preferences. Every field is optional:
 * only the ones sent are changed.
 *
 * @param country  the country the app shows, e.g. "US" or "IN" (its currency follows)
 * @param timeZone IANA name from the device, e.g. "Asia/Kolkata"
 */
public record PreferencesRequest(
        @Size(max = 20) List<@Valid @NotNull SectionSetting> dashboard,
        @Pattern(regexp = "^[A-Z]{2}$", message = "Country must be a 2-letter code like US or IN.") String country,
        @Size(max = 64) String timeZone) {

    public record SectionSetting(@NotBlank @Size(max = 40) String section, boolean visible) {
    }
}
