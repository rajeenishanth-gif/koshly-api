package com.koshly.user;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of PUT /api/users/current/preferences. */
public record PreferencesRequest(@NotNull @Size(max = 20) List<@Valid @NotNull SectionSetting> dashboard) {

    public record SectionSetting(@NotBlank @Size(max = 40) String section, boolean visible) {
    }
}
