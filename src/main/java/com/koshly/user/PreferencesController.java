package com.koshly.user;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/users/current/preferences: the signed-in user's app settings, saved on
 * the server so they follow the user to every device.
 *
 *   GET  the settings (defaults if none saved)
 *   PUT  replace them, e.g. {"dashboard":[{"section":"cards","visible":true}, ...]}
 */
@RestController
@RequestMapping("/api/users/current/preferences")
public class PreferencesController {

    private final PreferencesService service;

    public PreferencesController(PreferencesService service) {
        this.service = service;
    }

    @GetMapping
    public PreferencesResponse get(@AuthenticationPrincipal Jwt jwt) {
        return service.get(jwt);
    }

    @PutMapping
    public PreferencesResponse save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PreferencesRequest request) {
        return service.save(jwt, request);
    }
}
