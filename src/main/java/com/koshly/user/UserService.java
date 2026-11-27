package com.koshly.user;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.koshly.config.KoshlyProperties;
import com.koshly.region.AccountCountry;
import com.koshly.region.Market;

@Service
public class UserService {

    private final AppUserRepository users;
    private final AccountCountry accountCountry;
    private final String region;

    public UserService(AppUserRepository users, AccountCountry accountCountry, KoshlyProperties properties) {
        this.users = users;
        this.accountCountry = accountCountry;
        this.region = properties.region();
    }

    /**
     * Makes sure the signed-in user has an app_user row, so their data can point
     * to it. The row's country (and its currency and starting time zone) comes
     * from the sign-in's tenant and never changes.
     */
    @Transactional
    public void ensureUserExists(Jwt jwt) {
        Market m = accountCountry.of(jwt);
        users.insertIfMissing(jwt.getSubject(), jwt.getClaimAsString("email"), region, m.country(), m.currency(),
                m.defaultTimeZone());
    }

    /** The user's country, currency and time zone (their country's defaults before they have a row). */
    @Transactional(readOnly = true)
    public UserSettings settingsFor(Jwt jwt) {
        return users.findByFirebaseUid(jwt.getSubject())
                .map(u -> new UserSettings(u.getCountry(), u.getCurrency(), ZoneId.of(u.getTimeZone())))
                .orElseGet(() -> {
                    Market m = accountCountry.of(jwt);
                    return new UserSettings(m.country(), m.currency(), ZoneId.of(m.defaultTimeZone()));
                });
    }

    /** Today's date where the user lives, so "due today" matches their calendar. */
    public LocalDate today(Jwt jwt) {
        return LocalDate.now(Clock.system(settingsFor(jwt).timeZone()));
    }

    public record UserSettings(String country, String currency, ZoneId timeZone) {
    }
}
