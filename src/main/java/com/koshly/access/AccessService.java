package com.koshly.access;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.koshly.accountlink.CountryLink;
import com.koshly.accountlink.CountryLinkRepository;
import com.koshly.config.AppSwitches;
import com.koshly.region.AccountCountry;
import com.koshly.region.Market;
import com.koshly.user.AppUser;
import com.koshly.user.AppUserRepository;

/**
 * Who may use Koshly right now, and how many countries.
 *
 * <ul>
 *   <li><b>Home account:</b> the one the person registered with. It pays, and only a US or
 *       Canadian home may add countries. An added account (see country_link) is never a home.</li>
 *   <li><b>Phone:</b> with config/app.requirePhoneVerification on, a home account needs a
 *       confirmed mobile number (Firebase puts it in the sign-in token as phone_number).</li>
 *   <li><b>Subscription:</b> with config/app.requireSubscription on, the home account needs an
 *       active plan; its added countries share it, up to the plan's number of countries.</li>
 * </ul>
 * Both switches are off until billing and text messages are live: then everyone has access
 * and adding countries isn't limited by a plan.
 */
@Service
public class AccessService {

    private final AppSwitches switches;
    private final CountryLinkRepository links;
    private final SubscriptionRepository subscriptions;
    private final AccountCountry accountCountry;
    private final AppUserRepository users;
    private final Clock clock = Clock.systemUTC();

    public AccessService(AppSwitches switches, CountryLinkRepository links, SubscriptionRepository subscriptions,
            AccountCountry accountCountry, AppUserRepository users) {
        this.users = users;
        this.switches = switches;
        this.links = links;
        this.subscriptions = subscriptions;
        this.accountCountry = accountCountry;
    }

    /** The home account's user ID: the person's own for a home account, else the one it was added from. */
    @Transactional(readOnly = true)
    public String homeOf(String uid) {
        return links.findById(uid).map(CountryLink::getHomeUserId).orElse(uid);
    }

    /** True when this account was registered directly, not added from another country. */
    @Transactional(readOnly = true)
    public boolean isHome(String uid) {
        return !links.existsById(uid);
    }

    /** The plan on a home account, if it's active now. */
    @Transactional(readOnly = true)
    public Optional<Subscription> activeSubscription(String homeUid) {
        Instant now = Instant.now(clock);
        return subscriptions.findById(homeUid).filter(s -> s.isActiveAt(now));
    }

    /** Countries the person uses: their home plus each one they added. */
    @Transactional(readOnly = true)
    public List<String> addedCountries(String homeUid) {
        return links.findByHomeUserId(homeUid).stream().map(CountryLink::getLinkedCountry).toList();
    }

    /**
     * How many countries the person may have, home included. Unlimited while subscriptions
     * aren't required; otherwise the active plan's number, or 0 without a plan.
     */
    @Transactional(readOnly = true)
    public int allowedCountries(String homeUid) {
        if (!switches.requireSubscription()) {
            return Integer.MAX_VALUE;
        }
        return activeSubscription(homeUid).map(Subscription::getMaxCountries).orElse(0);
    }

    /** True when the sign-in carries a confirmed mobile number. */
    public static boolean phoneVerified(Jwt jwt) {
        String phone = jwt.getClaimAsString("phone_number");
        return phone != null && !phone.isBlank();
    }

    /** What stops this sign-in from using its money data right now, if anything. */
    @Transactional(readOnly = true)
    public Optional<Block> blocked(Jwt jwt) {
        String uid = jwt.getSubject();
        // Switches first: while both are off (today) this costs no database lookup.
        if (switches.requirePhoneVerification() && !phoneVerified(jwt) && isHome(uid)) {
            return Optional.of(Block.PHONE_REQUIRED);
        }
        if (switches.requireSubscription() && activeSubscription(homeOf(uid)).isEmpty()) {
            return Optional.of(Block.SUBSCRIPTION_REQUIRED);
        }
        return Optional.empty();
    }

    /** Everything the app needs to decide which screen to show. */
    @Transactional(readOnly = true)
    public Status status(Jwt jwt) {
        String uid = jwt.getSubject();
        boolean home = isHome(uid);
        String homeUid = home ? uid : homeOf(uid);
        Market country = accountCountry.of(jwt);
        Optional<Subscription> row = subscriptions.findById(homeUid);
        Optional<Subscription> plan = row.filter(s -> s.isActiveAt(Instant.now(clock)));
        List<String> added = addedCountries(homeUid);
        int allowed = allowedCountries(homeUid);
        String homeCountry = home ? country.country()
                : users.findByFirebaseUid(homeUid).map(AppUser::getCountry).orElse(null);
        return new Status(
                country.country(),
                home,
                homeCountry,
                home && country.canAddCountries(),
                switches.requirePhoneVerification(),
                phoneVerified(jwt),
                switches.requireSubscription(),
                plan.isPresent(),
                row.isPresent() && plan.isEmpty(),
                plan.map(Subscription::getPlan).orElse(null),
                row.map(Subscription::getSource).orElse(null),
                row.map(Subscription::getExpiresAt).orElse(null),
                1 + added.size(),
                allowed == Integer.MAX_VALUE ? null : allowed,
                blocked(jwt).map(Block::name).orElse(null));
    }

    public enum Block {
        /** A home account without a confirmed mobile number (HTTP 428). */
        PHONE_REQUIRED,
        /** No active plan on the home account (HTTP 402). */
        SUBSCRIPTION_REQUIRED
    }

    /**
     * Reply to GET /api/access.
     *
     * @param homeCountry       the country of the home account (the one that pays)
     * @param subscriptionEnded there was a plan, but it ended (so the app says "renew")
     * @param countriesAllowed  null while subscriptions aren't required (no limit)
     * @param blocked          null, PHONE_REQUIRED or SUBSCRIPTION_REQUIRED
     */
    public record Status(
            String country,
            boolean home,
            String homeCountry,
            boolean canAddCountries,
            boolean phoneRequired,
            boolean phoneVerified,
            boolean subscriptionRequired,
            boolean subscribed,
            boolean subscriptionEnded,
            String plan,
            String subscriptionSource,
            Instant subscriptionExpiresAt,
            int countriesUsed,
            Integer countriesAllowed,
            String blocked) {
    }
}
