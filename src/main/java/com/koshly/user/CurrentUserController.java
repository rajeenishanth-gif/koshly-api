package com.koshly.user;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/current")
public class CurrentUserController {

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