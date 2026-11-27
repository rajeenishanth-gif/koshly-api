package com.koshly.user;

import java.time.ZoneId;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.koshly.config.KoshlyProperties;
import com.koshly.region.AccountCountry;
import com.koshly.region.Market;

@Service
public class PreferencesService {

    private final AppUserRepository users;
    private final UserService userService;
    private final AccountCountry accountCountry;
    private final String region;

    public PreferencesService(AppUserRepository users, UserService userService, AccountCountry accountCountry,
            KoshlyProperties properties) {
        this.accountCountry = accountCountry;
        this.users = users;
        this.userService = userService;
        this.region = properties.region();
    }

    /** The user's settings, or the defaults if they haven't saved any. */
    @Transactional(readOnly = true)
    public PreferencesResponse get(Jwt jwt) {
        return users.findByFirebaseUid(jwt.getSubject())
                .map(PreferencesService::toResponse)
                .orElseGet(() -> {
                    // No row yet: the defaults for the account's country (from its sign-in).
                    Market m = accountCountry.of(jwt);
                    return new PreferencesResponse(DashboardLayout.defaults(), m.country(), m.currency(),
                            m.defaultTimeZone(), region);
                });
    }

    /** Changes only the fields that were sent. */
    @Transactional
    public PreferencesResponse save(Jwt jwt, PreferencesRequest request) {
        List<DashboardLayout.Section> sections = request.dashboard() == null ? null : request.dashboard().stream()
                .map(s -> new DashboardLayout.Section(s.section(), s.visible()))
                .toList();
        if (sections != null) {
            badRequestIf(DashboardLayout.problemWith(sections));
        }
        if (request.timeZone() != null && !ZoneId.getAvailableZoneIds().contains(request.timeZone())) {
            throw badRequest("Unknown time zone.");
        }

        userService.ensureUserExists(jwt);
        AppUser user = users.findByFirebaseUid(jwt.getSubject()).orElseThrow();
        if (sections != null) {
            user.setDashboardSections(DashboardLayout.format(sections));
        }
        if (request.timeZone() != null) {
            user.setTimeZone(request.timeZone());
        }
        users.flush();
        return toResponse(user);
    }

    private static PreferencesResponse toResponse(AppUser user) {
        return new PreferencesResponse(DashboardLayout.parse(user.getDashboardSections()), user.getCountry(),
                user.getCurrency(), user.getTimeZone(), user.getDataRegion());
    }

    private static void badRequestIf(String problem) {
        if (problem != null) {
            throw badRequest(problem);
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
