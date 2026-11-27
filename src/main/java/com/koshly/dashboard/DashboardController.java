package com.koshly.dashboard;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/dashboard : net balance, cash and debt totals, credit utilization,
 * payments due in the next 30 days, and promo-rate alerts, in the account's
 * country and currency.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    public DashboardResponse summary(@AuthenticationPrincipal Jwt jwt) {
        return service.summary(jwt);
    }
}
