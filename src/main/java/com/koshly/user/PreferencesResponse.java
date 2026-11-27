package com.koshly.user;

import java.util.List;

/**
 * The user's app settings.
 *
 * @param country    the account's country, from its sign-in; never changes
 * @param dataRegion where this user's data is stored (e.g. "us"); read-only
 */
public record PreferencesResponse(
        List<DashboardLayout.Section> dashboard,
        String country,
        String currency,
        String timeZone,
        String dataRegion) {
}
