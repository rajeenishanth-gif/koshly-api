package com.koshly.payoff;

import java.math.BigDecimal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/payoff-plan?monthly=1000&cushion=500&strategy=avalanche&extraLoans=id1,id2
 *
 * Which card or loan to pay, how much and why, for the signed-in account. All
 * parameters are optional: no "monthly" means just the minimums and loan
 * payments, no "cushion" means 0, no "strategy" means avalanche (highest rate
 * first), no "extraLoans" means auto and personal loans may get extra payments
 * while mortgages and student loans stay on their schedule.
 */
@RestController
@RequestMapping("/api/payoff-plan")
public class PayoffController {

    private final PayoffService service;

    public PayoffController(PayoffService service) {
        this.service = service;
    }

    @GetMapping
    public PayoffPlanResponse plan(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) BigDecimal monthly,
            @RequestParam(required = false) BigDecimal cushion,
            @RequestParam(required = false) String strategy,
            @RequestParam(required = false) String extraLoans) {
        return service.plan(jwt, monthly, cushion, strategy, extraLoans);
    }
}
