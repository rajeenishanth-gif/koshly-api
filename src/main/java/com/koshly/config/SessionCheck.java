package com.koshly.config;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

/**
 * Extra checks for sensitive actions (deleting the account, adding a country,
 * verifying a phone). A sign-in token stays valid for up to an hour, even after
 * the user is disabled or signs out everywhere; these actions ask Firebase
 * whether the session is still active instead of trusting the token alone.
 */
public interface SessionCheck {

    /** 401 if the session was revoked or the user disabled or deleted since the token was issued. */
    void requireActive(Jwt jwt);

    /**
     * 403 unless the user signed in within {@code maxAge}: for actions that can't
     * be undone, someone who picks up an unlocked phone shouldn't be able to do them.
     */
    static void requireRecentSignIn(Jwt jwt, Duration maxAge, String message) {
        Object authTime = jwt.getClaims().get("auth_time");
        Instant signedIn = authTime instanceof Number n ? Instant.ofEpochSecond(n.longValue())
                : authTime instanceof Instant i ? i
                : authTime instanceof Date d ? d.toInstant()
                : null;
        if (signedIn == null || signedIn.isBefore(Instant.now().minus(maxAge))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }
}
