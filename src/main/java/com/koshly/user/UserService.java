package com.koshly.user;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.koshly.config.KoshlyProperties;

@Service
public class UserService {

    private final AppUserRepository users;
    private final String region;

    public UserService(AppUserRepository users, KoshlyProperties properties) {
        this.users = users;
        this.region = properties.region();
    }

    /** Makes sure the signed-in user has an app_user row, so their data can point to it. */
    @Transactional
    public void ensureUserExists(Jwt jwt) {
        users.insertIfMissing(jwt.getSubject(), jwt.getClaimAsString("email"), region);
    }

    /** The user's currency and time zone (US defaults before they've saved anything). */
    @Transactional(readOnly = true)
    public UserSettings settingsFor(Jwt jwt) {
        return users.findByFirebaseUid(jwt.getSubject())
                .map(u -> new UserSettings(u.getCountry(), u.getCurrency(), ZoneId.of(u.getTimeZone())))
                .orElse(UserSettings.DEFAULT);
    }

    /** Today's date where the user lives, so "due today" matches their calendar. */
    public LocalDate today(Jwt jwt) {
        return LocalDate.now(Clock.system(settingsFor(jwt).timeZone()));
    }

    public record UserSettings(String country, String currency, ZoneId timeZone) {
        public static final UserSettings DEFAULT = new UserSettings("US", "USD", ZoneId.of("America/New_York"));
    }
}
