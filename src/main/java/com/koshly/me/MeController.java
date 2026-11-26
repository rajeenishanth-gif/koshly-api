package com.koshly.me;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return new MeResponse(
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