package com.koshly.user;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of PUT /api/users/current/preferences. Every field is optional:
 * only the ones sent are changed.
 *
 * @param timeZone IANA name from the device, e.g. "Asia/Kolkata"
 */
public record PreferencesRequest(
        @Size(max = 20) List<@Valid @NotNull SectionSetting> dashboard,
        @Size(max = 64) String timeZone) {

    public record SectionSetting(@NotBlank @Size(max = 40) String section, boolean visible) {
    }
}
