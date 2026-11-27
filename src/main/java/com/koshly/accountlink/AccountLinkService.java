package com.koshly.accountlink;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.koshly.region.AccountCountry;
import com.koshly.region.Market;
import com.koshly.user.UserService;

/**
 * "Add a country": a signed-in user proves an email address or mobile number
 * is theirs with a 6-digit code, then gets signed in to their separate
 * account in that country (created the first time).
 */
@Service
public class AccountLinkService {

    static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    static final int MAX_ATTEMPTS = 5;
    static final int MAX_CODES_PER_HOUR = 5;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^\\+[1-9][0-9]{7,14}$"); // E.164, e.g. +919876543210

    private final VerificationCodeRepository codes;
    private final CodeSender sender;
    private final TenantAccounts tenants;
    private final AccountCountry accountCountry;
    private final UserService users;
    private final BCryptPasswordEncoder hasher = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock = Clock.systemUTC();

    public AccountLinkService(VerificationCodeRepository codes, CodeSender sender, TenantAccounts tenants,
            AccountCountry accountCountry, UserService users) {
        this.codes = codes;
        this.sender = sender;
        this.tenants = tenants;
        this.accountCountry = accountCountry;
        this.users = users;
    }

    /** Sends a code to the email or phone, for adding the given country. */
    @Transactional
    public CodeSent sendCode(Jwt jwt, String country, Channel channel, String rawDestination) {
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
        Instant now = Instant.now(clock);
        if (codes.countByUserIdAndCreatedAtAfter(jwt.getSubject(), now.minus(Duration.ofHours(1))) >= MAX_CODES_PER_HOUR) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many codes requested. Please wait an hour and try again.");
        }

        String code = "%06d".formatted(random.nextInt(1_000_000));
        VerificationCode saved = codes.save(new VerificationCode(jwt.getSubject(), target.country(), channel,
                destination, hasher.encode(code), now.plus(CODE_LIFETIME)));
        sender.send(channel, destination, code);
        return new CodeSent(saved.getId(), channel.value(), mask(channel, destination), saved.getExpiresAt());
    }

    /** Checks the code; if right, returns a sign-in token for that country's account. */
    @Transactional(noRollbackFor = ResponseStatusException.class) // a wrong try still counts
    public LinkedAccount verify(Jwt jwt, UUID id, String code) {
        VerificationCode vc = codes.findByIdAndUserId(id, jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That code request wasn't found."));
        Instant now = Instant.now(clock);
        if (vc.getUsedAt() != null || now.isAfter(vc.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.GONE, "This code has expired. Ask for a new one.");
        }
        if (vc.getAttempts() >= MAX_ATTEMPTS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many tries. Ask for a new code.");
        }
        vc.countAttempt();
        if (code == null || !hasher.matches(code.trim(), vc.getCodeHash())) {
            int left = MAX_ATTEMPTS - vc.getAttempts();
            throw bad(left > 0 ? "That code isn't right. " + left + " tr" + (left == 1 ? "y" : "ies") + " left."
                    : "That code isn't right. Ask for a new code.");
        }
        vc.markUsed(now);

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
        } else {
            v = v.replaceAll("[\\s()-]", "");
            if (!PHONE.matcher(v).matches()) {
                throw bad("Enter the mobile number with its country code, like +91 98765 43210.");
            }
        }
        return v;
    }

    /** "n•••@gmail.com" or "+91 •••• ••3210", so the reply doesn't reveal the whole address. */
    static String mask(Channel channel, String d) {
        if (channel == Channel.EMAIL) {
            int at = d.indexOf('@');
            return d.charAt(0) + "•••" + d.substring(at);
        }
        return d.substring(0, Math.min(3, d.length())) + " •••• ••" + d.substring(d.length() - 4);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    /** Reply to "send code". */
    public record CodeSent(UUID verificationId, String channel, String sentTo, Instant expiresAt) {
    }

    /** Reply to "verify": the app signs in to that country with this token. */
    public record LinkedAccount(String country, String signInToken) {
    }
}
