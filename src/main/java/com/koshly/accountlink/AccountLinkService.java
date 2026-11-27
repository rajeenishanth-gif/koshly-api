package com.koshly.accountlink;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.koshly.region.AccountCountry;
import com.koshly.region.Market;
import com.koshly.user.UserService;

/**
 * "Add a country": a signed-in user proves an email address or mobile number
 * is theirs with a 6-digit code ({@link OneTimeCodes}), then gets signed in to
 * their separate account in that country (created the first time).
 */
@Service
public class AccountLinkService {

    static final int MAX_CODES_PER_HOUR = OneTimeCodes.MAX_CODES_PER_HOUR;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]{2,}$");
    /** E.164, e.g. +919876543210 */
    public static final Pattern PHONE = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

    private final OneTimeCodes codes;
    private final TenantAccounts tenants;
    private final AccountCountry accountCountry;
    private final UserService users;

    public AccountLinkService(OneTimeCodes codes, TenantAccounts tenants, AccountCountry accountCountry,
            UserService users) {
        this.codes = codes;
        this.tenants = tenants;
        this.accountCountry = accountCountry;
        this.users = users;
    }

    /** Sends a code to the email or phone, for adding the given country. */
    @Transactional
    public OneTimeCodes.CodeSent sendCode(Jwt jwt, String country, Channel channel, String rawDestination) {
        Market target = Market.forCountry(country)
                .filter(accountCountry::isEnabled)
                .orElseThrow(() -> bad("Koshly isn't available in that country yet."));
        if (target == accountCountry.of(jwt)) {
            throw bad("You're already using this country.");
        }
        if (accountCountry.tenantFor(target).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Adding " + target.country() + " isn't available yet.");
        }
        String destination = normalize(channel, rawDestination);
        users.ensureUserExists(jwt);
        return codes.issue(jwt.getSubject(), target.country(), CodePurpose.ADD_COUNTRY, channel, destination);
    }

    /** Checks the code; if right, returns a sign-in token for that country's account. */
    @Transactional(noRollbackFor = ResponseStatusException.class) // a wrong try still counts
    public LinkedAccount verify(Jwt jwt, UUID id, String code) {
        VerificationCode vc = codes.use(jwt.getSubject(), id, CodePurpose.ADD_COUNTRY, code);
        Market target = Market.forCountry(vc.getCountry()).orElseThrow();
        String tenant = accountCountry.tenantFor(target).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Adding that country isn't available yet."));
        String token = tenants.signInTokenFor(tenant, vc.getChannel(), vc.getDestination());
        return new LinkedAccount(target.country(), token);
    }

    private static String normalize(Channel channel, String raw) {
        String v = raw == null ? "" : raw.trim();
        if (channel == Channel.EMAIL) {
            v = v.toLowerCase(Locale.ROOT);
            if (v.length() > 254 || !EMAIL.matcher(v).matches()) {
                throw bad("Enter a valid email address.");
            }
            return v;
        }
        return normalizePhone(v);
    }

    /** "+1 (469) 555-0142" -> "+14695550142"; 400 if it isn't a full international number. */
    public static String normalizePhone(String raw) {
        String v = raw == null ? "" : raw.trim().replaceAll("[\\s()-]", "");
        if (!PHONE.matcher(v).matches()) {
            throw bad("Enter the mobile number with its country code, like +1 469 555 0142.");
        }
        return v;
    }

    static String mask(Channel channel, String d) {
        return OneTimeCodes.mask(channel, d);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    /** Reply to "verify": the app signs in to that country with this token. */
    public record LinkedAccount(String country, String signInToken) {
    }
}
