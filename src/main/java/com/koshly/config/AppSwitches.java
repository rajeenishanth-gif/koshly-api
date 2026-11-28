package com.koshly.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.cloud.firestore.DocumentSnapshot;
import com.koshly.region.Market;

/**
 * App-wide switches an admin changes without a redeploy: the Firestore document
 * {@code config/app}, edited in the Firebase console. The app reads the same
 * document, so the screens and the API always agree.
 *
 * <p>These are settings for the whole app, never for one customer. Today:
 * {@code enabledCountries}, e.g. ["US", "CA"]; {@code requireSubscription} and
 * {@code requirePhoneVerification}, true or false. (The app also reads
 * {@code enabledLanguages}; the API doesn't need it.)
 *
 * <p>Without the document or the field, or when Firestore can't be reached, the
 * deploy setting {@code koshly.accounts.enabled-countries} applies, so a missing
 * document never opens or closes anything by surprise. The value is cached for
 * {@code koshly.app-config.refresh} (default one minute) per instance.
 */
@Component
public class AppSwitches {

    private static final Logger log = LoggerFactory.getLogger(AppSwitches.class);

    private final FirebaseAdmin firebase;
    private final boolean fromFirestore;
    private final Duration refresh;
    private final List<String> defaultCountries;
    private final boolean defaultRequireSubscription;
    private final boolean defaultRequirePhone;
    private final Clock clock = Clock.systemUTC();

    /** The last values read (or the defaults), replaced together. */
    private volatile Values values;
    private volatile Instant fetchedAt = Instant.MIN;

    AppSwitches(FirebaseAdmin firebase, KoshlyProperties properties,
            @Value("${koshly.app-config.firestore:true}") boolean fromFirestore,
            @Value("${koshly.app-config.refresh:PT1M}") Duration refresh,
            @Value("${koshly.app-config.require-subscription:false}") boolean defaultRequireSubscription,
            @Value("${koshly.app-config.require-phone-verification:false}") boolean defaultRequirePhone) {
        this.firebase = firebase;
        this.fromFirestore = fromFirestore;
        this.refresh = refresh;
        var configured = properties.accounts().enabledCountries();
        this.defaultCountries = configured == null || configured.isEmpty() ? List.of("US") : List.copyOf(configured);
        this.defaultRequireSubscription = defaultRequireSubscription;
        this.defaultRequirePhone = defaultRequirePhone;
        this.values = new Values(defaultCountries, defaultRequireSubscription, defaultRequirePhone);
    }

    /** Countries open to users right now. */
    public List<String> enabledCountries() {
        return current().countries();
    }

    /**
     * True when people need an active subscription to see their money ({@code requireSubscription}).
     * Off until billing is live; the subscription page and the server check are ready for it.
     */
    public boolean requireSubscription() {
        return current().requireSubscription();
    }

    /**
     * True when a home account must have a confirmed mobile number before it can be used
     * ({@code requirePhoneVerification}). Off until text messages are set up (Twilio).
     */
    public boolean requirePhoneVerification() {
        return current().requirePhone();
    }

    private Values current() {
        if (fromFirestore && Instant.now(clock).isAfter(fetchedAt.plus(refresh))) {
            reload();
        }
        return values;
    }

    private synchronized void reload() {
        if (!Instant.now(clock).isAfter(fetchedAt.plus(refresh))) {
            return; // another request just did it
        }
        try {
            DocumentSnapshot doc = firebase.firestore().collection("config").document("app").get()
                    .get(3, TimeUnit.SECONDS);
            values = doc.exists()
                    ? new Values(
                            parseCountries(doc.get("enabledCountries"), defaultCountries),
                            parseFlag(doc.get("requireSubscription"), defaultRequireSubscription),
                            parseFlag(doc.get("requirePhoneVerification"), defaultRequirePhone))
                    : new Values(defaultCountries, defaultRequireSubscription, defaultRequirePhone);
        } catch (Exception e) {
            // Keep the last known values (the deploy settings until a read works). Retried after `refresh`.
            log.warn("Couldn't read config/app from Firestore, using {}: {}", values, e.toString());
        } finally {
            fetchedAt = Instant.now(clock);
        }
    }

    /** The countries in the document's list that Koshly knows; the fallback when there are none. */
    static List<String> parseCountries(Object value, List<String> fallback) {
        if (!(value instanceof List<?> list)) {
            return fallback;
        }
        List<String> known = list.stream()
                .filter(String.class::isInstance)
                .map(c -> ((String) c).trim().toUpperCase())
                .filter(c -> Market.forCountry(c).isPresent())
                .distinct()
                .toList();
        return known.isEmpty() ? fallback : known;
    }

    /** A true/false field; anything else (missing, text, a number) means the fallback. */
    static boolean parseFlag(Object value, boolean fallback) {
        return value instanceof Boolean b ? b : fallback;
    }

    private record Values(List<String> countries, boolean requireSubscription, boolean requirePhone) {
    }
}
