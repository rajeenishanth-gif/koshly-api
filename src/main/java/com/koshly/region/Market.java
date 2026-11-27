package com.koshly.region;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Countries Koshly supports, with each one's defaults.
 *
 * dataRegion is where that country's data should live. Today everything runs
 * in "us". When India gets its own deployment (Cloud SQL in asia-south1),
 * Indian users' data moves to the "in" region and its API; see the guide doc.
 *
 * To add a country: add a line here and in the app's src/lib/region/markets.ts.
 */
public enum Market {

    US("US", "USD", "us", "America/New_York", "plaid", "+1"),
    IN("IN", "INR", "in", "Asia/Kolkata", "account_aggregator", "+91");

    private final String country;
    private final String currency;
    private final String dataRegion;
    private final String defaultTimeZone;
    private final String bankLinkProvider;
    private final String dialCode;

    Market(String country, String currency, String dataRegion, String defaultTimeZone, String bankLinkProvider,
            String dialCode) {
        this.dialCode = dialCode;
        this.country = country;
        this.currency = currency;
        this.dataRegion = dataRegion;
        this.defaultTimeZone = defaultTimeZone;
        this.bankLinkProvider = bankLinkProvider;
    }

    /** ISO 3166 country code, e.g. "IN". */
    public String country() {
        return country;
    }

    /** ISO 4217 currency code, e.g. "INR". */
    public String currency() {
        return currency;
    }

    public String dataRegion() {
        return dataRegion;
    }

    public String defaultTimeZone() {
        return defaultTimeZone;
    }

    /** Which BankDataProvider links banks there: "plaid" (US) or "account_aggregator" (India). */
    public String bankLinkProvider() {
        return bankLinkProvider;
    }

    /** International dialing code for its mobile numbers, e.g. "+91". */
    public String dialCode() {
        return dialCode;
    }

    public static Optional<Market> forCountry(String country) {
        return Arrays.stream(values()).filter(m -> m.country.equals(country)).findFirst();
    }

    /** Currencies accounts may use: every supported country's currency. */
    public static final Set<String> CURRENCIES =
            Arrays.stream(values()).map(Market::currency).collect(Collectors.toUnmodifiableSet());

    public static final Set<String> COUNTRIES =
            Arrays.stream(values()).map(Market::country).collect(Collectors.toUnmodifiableSet());
}
