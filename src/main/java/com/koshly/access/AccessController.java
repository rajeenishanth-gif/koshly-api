package com.koshly.access;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/access: what the signed-in person may do right now. The app calls it after
 * sign-in to choose between the dashboard, "confirm your mobile number" and the
 * subscription page. Always allowed, so a blocked person can still see why.
 */
@RestController
@RequestMapping("/api/access")
public class AccessController {

    private final AccessService access;

    public AccessController(AccessService access) {
        this.access = access;
    }

    @GetMapping
    public AccessService.Status status(@AuthenticationPrincipal Jwt jwt) {
        return access.status(jwt);
    }
}
