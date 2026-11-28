package com.koshly.accountlink;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.koshly.access.AccessService;
import com.koshly.config.SessionCheck;
import com.koshly.region.AccountCountry;
import com.koshly.region.Market;
import com.koshly.region.MobileNumbers;
import com.koshly.user.UserService;

/**
 * "Add a country": a person signed in to their home account opens their separate
 * account in another country.
 *
 * <ol>
 *   <li>{@link #add}: if they added that country before, or it's new, they're signed
 *       straight in (a new account is created). Being signed in to the home account is
 *       the proof; the bank connection there proves the rest.</li>
 *   <li>Only if an account with their email already exists in that country, made some
 *       other way, they must prove it's theirs with a 6-digit code ({@link #sendCode},
 *       {@link #verify}).</li>
 * </ol>
 * Rules: only a home account registered in the US or Canada may add countries; an added
 * account can't add more; with subscriptions required, the plan limits how many.
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
    private final SessionCheck sessions;
    private final CountryLinkRepository links;
    private final AccessService access;

    public AccountLinkService(OneTimeCodes codes, TenantAccounts tenants, AccountCountry accountCountry,
            UserService users, SessionCheck sessions, CountryLinkRepository links, AccessService access) {
        this.sessions = sessions;
        this.links = links;
        this.access = access;
        this.codes = codes;
        this.tenants = tenants;
        this.accountCountry = accountCountry;
        this.users = users;
    }

    /**
     * Opens the person's account in another country without a code when possible.
     *
     * @return {@link AddResult#signedIn} with a sign-in token, or {@link AddResult#needsCode}
     *         when an account with their email already exists there (then: {@link #sendCode})
     */
    @Transactional
    public AddResult add(Jwt jwt, String country) {
        Market target = checkCanAdd(jwt, country);
        sessions.requireActive(jwt); // opening another account: make sure this session wasn't revoked
        String tenant = accountCountry.tenantFor(target).orElse(null); // null: the default country
        String home = jwt.getSubject();

        // Added before (on this or another device): straight back in.
        var existing = links.findByHomeUserIdAndLinkedCountry(home, target.country());
        if (existing.isPresent()) {
            return AddResult.signedIn(target.country(), tenants.signInToken(tenant, existing.get().getLinkedUserId()));
        }
        checkAllowance(home);

        String email = jwt.getClaimAsString("email");
        if (email != null && !email.isBlank()) {
            String normalized = email.trim().toLowerCase(Locale.ROOT);
            if (tenants.findByEmail(tenant, normalized).isPresent()) {
                // Someone registered there with this email before: prove it's them.
                return AddResult.needsCode(target.country(), mask(Channel.EMAIL, normalized));
            }
        }
        Boolean verified = jwt.getClaimAsBoolean("email_verified");
        String uid = tenants.create(tenant, email == null ? null : email.trim().toLowerCase(Locale.ROOT),
                Boolean.TRUE.equals(verified));
        users.ensureUserExists(jwt);
        links.save(new CountryLink(uid, target.country(), home));
        return AddResult.signedIn(target.country(), tenants.signInToken(tenant, uid));
    }

    /** Sends a code to the email or phone, for adding the given country. */
    @Transactional
    public OneTimeCodes.CodeSent sendCode(Jwt jwt, String country, Channel channel, String rawDestination) {
        Market target = checkCanAdd(jwt, country);
        if (links.findByHomeUserIdAndLinkedCountry(jwt.getSubject(), target.country()).isEmpty()) {
            checkAllowance(jwt.getSubject());
        }
        String destination = normalize(channel, rawDestination);
        if (channel == Channel.SMS && !MobileNumbers.allowedFor(target, destination)) {
            throw bad(MobileNumbers.notAllowedMessage(target));
        }
        sessions.requireActive(jwt); // opening another account: make sure this session wasn't revoked
        users.ensureUserExists(jwt);
        return codes.issue(jwt.getSubject(), target.country(), CodePurpose.ADD_COUNTRY, channel, destination);
    }

    /** Checks the code; if right, returns a sign-in token for that country's account. */
    @Transactional(noRollbackFor = ResponseStatusException.class) // a wrong try still counts
    public LinkedAccount verify(Jwt jwt, UUID id, String code) {
        sessions.requireActive(jwt);
        VerificationCode vc = codes.use(jwt.getSubject(), id, CodePurpose.ADD_COUNTRY, code);
        Market target = Market.forCountry(vc.getCountry()).orElseThrow();
        if (!accountCountry.canSignIn(target)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Adding that country isn't available yet.");
        }
        String tenant = accountCountry.tenantFor(target).orElse(null); // null: the default country
        TenantAccounts.TenantSignIn signIn = tenants.signInTokenFor(tenant, vc.getChannel(), vc.getDestination());
        // Remember that it's theirs, so next time it opens without a code. If that account is
        // already linked to another home, leave it: an account has one home.
        if (!links.existsById(signIn.uid())) {
            users.ensureUserExists(jwt);
            links.findByHomeUserIdAndLinkedCountry(jwt.getSubject(), target.country()).ifPresent(old -> {
                links.delete(old);
                links.flush(); // before the insert below, which the one-per-country rule would refuse
            });
            links.save(new CountryLink(signIn.uid(), target.country(), jwt.getSubject()));
        }
        return new LinkedAccount(target.country(), signIn.token());
    }

    /** The country to add, if this account may add it; 400/403/503 otherwise. */
    private Market checkCanAdd(Jwt jwt, String country) {
        Market target = Market.forCountry(country)
                .filter(accountCountry::isEnabled)
                .orElseThrow(() -> bad("Koshly isn't available in that country yet."));
        Market own = accountCountry.of(jwt);
        if (target == own) {
            throw bad("You're already using this country.");
        }
        if (!own.canAddCountries() || !access.isHome(jwt.getSubject())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Countries can be added only from an account registered in the US or Canada.");
        }
        if (!accountCountry.canSignIn(target)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Adding " + target.country() + " isn't available yet.");
        }
        return target;
    }

    /** 402 when the person's plan has no room for another country (only while subscriptions are required). */
    private void checkAllowance(String homeUid) {
        int used = 1 + access.addedCountries(homeUid).size();
        int allowed = access.allowedCountries(homeUid);
        if (used >= allowed) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, allowed == 0
                    ? "A Koshly subscription is needed to add a country."
                    : "Your plan covers " + allowed + (allowed == 1 ? " country" : " countries")
                            + ". Upgrade it to add another.");
        }
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

    /**
     * Reply to POST /api/account-links: signed in (with a token) or a code is needed first.
     *
     * @param codeSentTo where the code would go (masked), when needsCode
     */
    public record AddResult(String country, boolean needsCode, String signInToken, String codeSentTo) {
        static AddResult signedIn(String country, String token) {
            return new AddResult(country, false, token, null);
        }

        static AddResult needsCode(String country, String maskedEmail) {
            return new AddResult(country, true, null, maskedEmail);
        }
    }

    /** Reply to "verify": the app signs in to that country with this token. */
    public record LinkedAccount(String country, String signInToken) {
    }
}
