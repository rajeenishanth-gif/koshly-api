package com.koshly.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** How the admin's list in config/app is read. */
class AppSwitchesTest {

    private static final List<String> DEPLOY = List.of("US");

    @Test
    void usesTheDocumentsList() {
        assertThat(AppSwitches.parseCountries(List.of("US", "IN"), DEPLOY)).containsExactly("US", "IN");
    }

    @Test
    void tidiesCaseSpacesAndRepeats() {
        assertThat(AppSwitches.parseCountries(List.of(" in", "IN", "us"), DEPLOY)).containsExactly("IN", "US");
    }

    @Test
    void ignoresUnknownCountriesAndNonText() {
        assertThat(AppSwitches.parseCountries(List.of("US", "XX", 42), DEPLOY)).containsExactly("US");
    }

    @Test
    void flagsMustBeRealTrueOrFalse() {
        assertThat(AppSwitches.parseFlag(true, false)).isTrue();
        assertThat(AppSwitches.parseFlag(false, true)).isFalse();
        assertThat(AppSwitches.parseFlag("true", false)).isFalse(); // text isn't a switch
        assertThat(AppSwitches.parseFlag(null, false)).isFalse();
        assertThat(AppSwitches.parseFlag(null, true)).isTrue();
    }

    @Test
    void fallsBackToTheDeploySettingWhenMissingEmptyOrWrongType() {
        assertThat(AppSwitches.parseCountries(null, DEPLOY)).isEqualTo(DEPLOY);
        assertThat(AppSwitches.parseCountries(List.of(), DEPLOY)).isEqualTo(DEPLOY);
        assertThat(AppSwitches.parseCountries(List.of("XX"), DEPLOY)).isEqualTo(DEPLOY);
        assertThat(AppSwitches.parseCountries("US,IN", DEPLOY)).isEqualTo(DEPLOY);
    }
}
