package com.koshly.user;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/current")
public class CurrentUserController {

    private final AccountDeletionService deletion;

    public CurrentUserController(AccountDeletionService deletion) {
        this.deletion = deletion;
    }

    /** Deletes the account and everything in it, for good. 204 when done. */
    @DeleteMapping
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt) {
        deletion.deleteCurrentUser(jwt);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public CurrentUserResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
        return new CurrentUserResponse(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")),
                signInProvider(jwt));
    }

    // "password", "google.com" or "apple.com", from the token's "firebase" section
    private static String signInProvider(Jwt jwt) {
        Object firebase = jwt.getClaims().get("firebase");
        if (firebase instanceof Map<?, ?> map && map.get("sign_in_provider") instanceof String provider) {
            return provider;
        }
        return null;
    }
}