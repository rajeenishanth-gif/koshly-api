package com.koshly.accountlink;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 6-digit codes sent by email or text, shared by "Add a country" and phone
 * verification. Codes are stored hashed, last 10 minutes, allow 5 tries and
 * work once. Each user can ask for 5 codes an hour, whatever they're for.
 *
 * <p>Callers run inside their own transaction. {@link #use} counts a wrong try
 * before throwing, so the caller's transaction must not roll back on
 * ResponseStatusException.
 */
@Service
public class OneTimeCodes {

    public static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    public static final int MAX_ATTEMPTS = 5;
    public static final int MAX_CODES_PER_HOUR = 5;

    private final VerificationCodeRepository codes;
    private final CodeSender sender;
    private final BCryptPasswordEncoder hasher = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock = Clock.systemUTC();

    public OneTimeCodes(VerificationCodeRepository codes, CodeSender sender) {
        this.codes = codes;
        this.sender = sender;
    }

    /** Creates a code, sends it, and returns what the app needs to show. */
    public CodeSent issue(String userId, String country, CodePurpose purpose, Channel channel, String destination) {
        Instant now = Instant.now(clock);
        if (codes.countByUserIdAndCreatedAtAfter(userId, now.minus(Duration.ofHours(1))) >= MAX_CODES_PER_HOUR) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many codes requested. Please wait an hour and try again.");
        }
        String code = "%06d".formatted(random.nextInt(1_000_000));
        VerificationCode saved = codes.save(new VerificationCode(userId, country, purpose, channel, destination,
                hasher.encode(code), now.plus(CODE_LIFETIME)));
        sender.send(channel, destination, code);
        return new CodeSent(saved.getId(), channel.value(), mask(channel, destination), saved.getExpiresAt());
    }

    /**
     * Checks a code. Right: marks it used and returns it. Wrong, expired, used,
     * too many tries, someone else's or for another purpose: throws with a
     * message for the user.
     */
    public VerificationCode use(String userId, UUID id, CodePurpose purpose, String code) {
        VerificationCode vc = codes.findByIdAndUserId(id, userId)
                .filter(c -> c.getPurpose() == purpose)
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    left > 0 ? "That code isn't right. " + left + " tr" + (left == 1 ? "y" : "ies") + " left."
                            : "That code isn't right. Ask for a new code.");
        }
        vc.markUsed(now);
        return vc;
    }

    /** "n•••@gmail.com" or "+91 •••• ••3210", so a reply doesn't reveal the whole address. */
    public static String mask(Channel channel, String d) {
        if (channel == Channel.EMAIL) {
            int at = d.indexOf('@');
            return d.charAt(0) + "•••" + d.substring(at);
        }
        return d.substring(0, Math.min(3, d.length())) + " •••• ••" + d.substring(d.length() - 4);
    }

    /** Reply to "send a code". */
    public record CodeSent(UUID verificationId, String channel, String sentTo, Instant expiresAt) {
    }
}
