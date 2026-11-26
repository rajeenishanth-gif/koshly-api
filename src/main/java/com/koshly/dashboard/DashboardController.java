package com.koshly.dashboard;

import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/dashboard?country=IN : net balance, cash and debt totals, credit
 * utilization, payments due in the next 30 days, and promo-rate alerts, for
 * one country (the profile country if none is given).
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    public DashboardResponse summary(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @Pattern(regexp = "^[A-Z]{2}$") String country) {
        return service.summary(jwt, country);
    }
}
