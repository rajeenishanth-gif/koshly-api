package com.koshly.user;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The dashboard's sections in the user's order, each shown or hidden.
 *
 * Stored in app_user.dashboard_sections as one string, e.g.
 * "upcoming,cards,-promotions,subscriptions,accounts,loans" ("-" = hidden).
 * Sections the user hasn't placed (for example ones added in a later version
 * of the app) are added at the end, shown.
 */
public final class DashboardLayout {

    /** Every section the app knows, in the default order. */
    public static final List<String> SECTIONS =
            List.of("upcoming", "subscriptions", "promotions", "cards", "accounts", "loans");

    /** One section and whether it's shown. */
    public record Section(String section, boolean visible) {
    }

    private DashboardLayout() {
    }

    /** The default order, everything shown. */
    public static List<Section> defaults() {
        return complete(List.of());
    }

    /** Reads the stored string. Unknown or repeated names are skipped, so old data never breaks the app. */
    public static List<Section> parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return defaults();
        }
        List<Section> sections = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String part : stored.split(",")) {
            boolean hidden = part.startsWith("-");
            String name = hidden ? part.substring(1) : part;
            if (SECTIONS.contains(name) && seen.add(name)) {
                sections.add(new Section(name, !hidden));
            }
        }
        return complete(sections);
    }

    /** The string to store. Expects a list already checked by {@link #problemWith}. */
    public static String format(List<Section> sections) {
        return complete(sections).stream()
                .map(s -> (s.visible() ? "" : "-") + s.section())
                .collect(Collectors.joining(","));
    }

    /** Why a list from the app can't be saved, or null if it's fine. */
    public static String problemWith(List<Section> sections) {
        Set<String> seen = new LinkedHashSet<>();
        for (Section s : sections) {
            if (!SECTIONS.contains(s.section())) {
                return "Unknown section \"" + s.section() + "\". Known sections: " + String.join(", ", SECTIONS) + ".";
            }
            if (!seen.add(s.section())) {
                return "Section \"" + s.section() + "\" is listed more than once.";
            }
        }
        return null;
    }

    /** Adds any missing sections at the end, shown. */
    private static List<Section> complete(List<Section> sections) {
        List<Section> all = new ArrayList<>(sections);
        Set<String> present = sections.stream().map(Section::section).collect(Collectors.toSet());
        for (String name : SECTIONS) {
            if (!present.contains(name)) {
                all.add(new Section(name, true));
            }
        }
        return List.copyOf(all);
    }
}
