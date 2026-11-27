package com.koshly.config;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuthException;
import com.koshly.region.AccountCountry;

/** Asks Firebase (Admin SDK) whether the token was revoked or the user disabled. One network call. */
@Component
class FirebaseSessionCheck implements SessionCheck {

    private final FirebaseAdmin admin;

    FirebaseSessionCheck(FirebaseAdmin admin) {
        this.admin = admin;
    }

    @Override
    public void requireActive(Jwt jwt) {
        String tenant = AccountCountry.tenantOf(jwt).orElse(null);
        try {
            admin.auth(tenant).verifyIdToken(jwt.getTokenValue(), true);
        } catch (FirebaseAuthException e) {
            AuthErrorCode code = e.getAuthErrorCode();
            if (code == AuthErrorCode.REVOKED_ID_TOKEN || code == AuthErrorCode.USER_DISABLED
                    || code == AuthErrorCode.USER_NOT_FOUND || code == AuthErrorCode.EXPIRED_ID_TOKEN) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Your session has ended. Please sign in again.");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Couldn't check your session right now. Please try again.", e);
        }
    }
}
