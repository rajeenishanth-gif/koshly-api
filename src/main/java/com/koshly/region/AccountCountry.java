package com.koshly.region;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.koshly.config.AppSwitches;
import com.koshly.config.KoshlyProperties;

/**
 * Which country a signed-in Koshly account belongs to.
 *
 * Every account is for exactly one country, chosen at sign-up and never
 * changed. Firebase puts the tenant the user signed in through in the token
 * ("firebase": {"tenant": "india-k3x9q"}), and that tenant decides the country.
 * The app can't choose or change it, so an Indian account can never read or
 * write US data, and the other way round.
 */
@Component
public class AccountCountry {

    private final Map<String, String> tenantsByCountry;
    private final String defaultCountry;
    private final AppSwitches switches;

    AccountCountry(KoshlyProperties properties, AppSwitches switches) {
        this.switches = switches;
        var accounts = properties.accounts();
        this.tenantsByCountry = accounts.tenants() == null ? Map.of() : accounts.tenants();
        this.defaultCountry = accounts.defaultCountry() == null || accounts.defaultCountry().isBlank()
                ? null
                : accounts.defaultCountry();
        List<String> enabled = accounts.enabledCountries() == null ? List.of() : accounts.enabledCountries();
        // Catch typos at startup rather than at the first sign-in.
        enabled.forEach(c -> Market.forCountry(c)
                .orElseThrow(() -> new IllegalStateException("koshly.accounts.enabled-countries: unknown country " + c)));
        tenantsByCountry.keySet().forEach(c -> Market.forCountry(c)
                .orElseThrow(() -> new IllegalStateException("koshly.accounts.tenants: unknown country " + c)));
        if (defaultCountry != null) {
            Market.forCountry(defaultCountry).orElseThrow(() ->
                    new IllegalStateException("koshly.accounts.default-country: unknown country " + defaultCountry));
        }
    }

    /**
     * True when users can use this country now: switched on by an admin in Firestore
     * (config/app.enabledCountries, see AppSwitches), or by koshly.accounts.enabled-countries
     * when that isn't set.
     */
    public boolean isEnabled(Market market) {
        return switches.enabledCountries().contains(market.country());
    }

    /**
     * The account's country. 403 if the token's tenant isn't one of Koshly's
     * countries, or is a country that isn't open yet.
     */
    public Market of(Jwt jwt) {
        Market m = resolve(jwt);
        if (!isEnabled(m)) {
            throw forbidden();
        }
        return m;
    }

    private Market resolve(Jwt jwt) {
        String tenant = tenantOf(jwt).orElse(null);
        if (tenant == null) {
            if (defaultCountry == null) {
                throw forbidden();
            }
            return Market.forCountry(defaultCountry).orElseThrow();
        }
        return tenantsByCountry.entrySet().stream()
                .filter(e -> tenant.equals(e.getValue()))
                .findFirst()
                .flatMap(e -> Market.forCountry(e.getKey()))
                .orElseThrow(AccountCountry::forbidden);
    }

    /**
     * True when people can sign in to this country: it has its own tenant, or it's the
     * default country (sign-ins without a tenant, the US until tenants are set up).
     */
    public boolean canSignIn(Market market) {
        return tenantFor(market).isPresent() || (defaultCountry != null && defaultCountry.equals(market.country()));
    }

    /** The Firebase tenant ID for a country, or empty if it has none yet. */
    public Optional<String> tenantFor(Market market) {
        String t = tenantsByCountry.get(market.country());
        return t == null || t.isBlank() ? Optional.empty() : Optional.of(t);
    }

    /** The Firebase tenant the user signed in through, if any (none = the default country). */
    public static Optional<String> tenantOf(Jwt jwt) {
        if (jwt.getClaims().get("firebase") instanceof Map<?, ?> firebase
                && firebase.get("tenant") instanceof String tenant && !tenant.isBlank()) {
            return Optional.of(tenant);
        }
        return Optional.empty();
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "This sign-in isn't for a Koshly country.");
    }
}
