package com.koshly.user;

import java.util.List;

/** The user's app settings. Today: the dashboard's section order. */
public record PreferencesResponse(List<DashboardLayout.Section> dashboard) {
}
