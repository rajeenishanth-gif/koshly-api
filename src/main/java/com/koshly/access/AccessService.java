package com.koshly.access;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.koshly.account.Account;
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
 *   <li><b>Subscription:</b> with config/app.requireSubscription on, a home account without an
 *       active plan is on the <b>free tier</b>: its own country only, things added by hand only
 *       (no bank linking), and at most 1 bank account, 2 credit cards and 1 loan. A plan lifts
 *       the limits and covers added countries, up to the plan's number of countries. An added
 *       country without a plan behind it can't be used (the free tier is the home country only).</li>
 * </ul>
 * Both switches are off until billing and text messages are live: then everyone has access,
 * nothing is limited, and adding countries isn't limited by a plan.
 */
@Service
public class AccessService {

    private final AppSwitches switches;
    private final CountryLinkRepository links;
    private final SubscriptionRepository subscriptions;
    private final AccountCountry accountCountry;
    private final AppUserRepository users;
    /** Free tier: how many of each kind may be added by hand (Account.DEPOSITORY, CREDIT, LOAN). */
    private final Map<String, Integer> freeLimits;
    private final Clock clock = Clock.systemUTC();

    public AccessService(AppSwitches switches, CountryLinkRepository links, SubscriptionRepository subscriptions,
            AccountCountry accountCountry, AppUserRepository users,
            @Value("${koshly.free-tier.bank-accounts:1}") int freeBankAccounts,
            @Value("${koshly.free-tier.credit-cards:2}") int freeCards,
            @Value("${koshly.free-tier.loans:1}") int freeLoans) {
        this.users = users;
        this.freeLimits = Map.of(Account.DEPOSITORY, freeBankAccounts, Account.CREDIT, freeCards, Account.LOAN, freeLoans);
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
     * aren't required; otherwise the active plan's number, or 1 (the home country) on the free tier.
     */
    @Transactional(readOnly = true)
    public int allowedCountries(String homeUid) {
        if (!switches.requireSubscription()) {
            return Integer.MAX_VALUE;
        }
        return activeSubscription(homeUid).map(Subscription::getMaxCountries).orElse(1);
    }

    /** True when this account is on the free tier: subscriptions are required and there's no active plan. */
    @Transactional(readOnly = true)
    public boolean onFreeTier(String uid) {
        return switches.requireSubscription() && activeSubscription(homeOf(uid)).isEmpty();
    }

    /** The free tier's limit for a kind of account (Account.DEPOSITORY, CREDIT or LOAN). */
    public int freeLimit(String type) {
        return freeLimits.getOrDefault(type, 0);
    }

    /**
     * 402 when the free tier has no room for another of this kind; nothing on a paid plan or
     * while subscriptions aren't required. Things already added stay (after a plan ends, say):
     * only adding more is stopped.
     */
    @Transactional(readOnly = true)
    public void checkFreeTierRoom(String uid, String type, long alreadyThere) {
        if (onFreeTier(uid) && alreadyThere >= freeLimit(type)) {
            throw paidFeature("FREE_LIMIT", "The free plan includes " + freeLimits.get(Account.DEPOSITORY)
                    + " bank account, " + freeLimits.get(Account.CREDIT) + " credit cards and "
                    + freeLimits.get(Account.LOAN) + " loan. Upgrade to add more.");
        }
    }

    /** 402 for features only paid plans have (bank linking), while on the free tier. */
    @Transactional(readOnly = true)
    public void requirePaidPlan(String uid) {
        if (onFreeTier(uid)) {
            throw paidFeature("PAID_FEATURE", "Bank linking is part of the paid plans. Upgrade to connect your bank.");
        }
    }

    private static ErrorResponseException paidFeature(String code, String message) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYMENT_REQUIRED, message);
        problem.setProperty("code", code);
        return new ErrorResponseException(HttpStatus.PAYMENT_REQUIRED, problem, null);
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
        // The free tier covers the home country only: an added country needs a plan behind it.
        if (switches.requireSubscription() && !isHome(uid) && activeSubscription(homeOf(uid)).isEmpty()) {
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
                switches.requireSubscription() && plan.isEmpty(),
                Map.of("bankAccounts", freeLimit(Account.DEPOSITORY), "creditCards", freeLimit(Account.CREDIT),
                        "loans", freeLimit(Account.LOAN)),
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
        /** An added country with no active plan on its home account (HTTP 402). */
        SUBSCRIPTION_REQUIRED
    }

    /**
     * Reply to GET /api/access.
     *
     * @param homeCountry       the country of the home account (the one that pays)
     * @param freeTier          subscriptions are required and there's no active plan: limits apply
     * @param freeLimits        the free tier's limits: bankAccounts, creditCards, loans
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
            boolean freeTier,
            Map<String, Integer> freeLimits,
            boolean subscriptionEnded,
            String plan,
            String subscriptionSource,
            Instant subscriptionExpiresAt,
            int countriesUsed,
            Integer countriesAllowed,
            String blocked) {
    }
}
