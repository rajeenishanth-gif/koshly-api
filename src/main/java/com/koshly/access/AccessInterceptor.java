package com.koshly.access;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Stops money data reaching someone who needs to confirm their phone or subscribe,
 * whatever the app shows: the server decides, so skipping a screen doesn't help.
 *
 *   428 Precondition Required  {"code":"PHONE_REQUIRED"}         confirm the mobile number first
 *   402 Payment Required       {"code":"SUBSCRIPTION_REQUIRED"}  subscribe or renew first
 *
 * Not applied to the endpoints a blocked person still needs (see AccessConfig): their
 * profile and phone, deleting the account, /api/access and adding a country.
 */
public class AccessInterceptor implements HandlerInterceptor {

    private final AccessService access;

    public AccessInterceptor(AccessService access) {
        this.access = access;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken token)) return true;
        access.blocked(token.getToken()).ifPresent(block -> {
            throw new Blocked(block);
        });
        return true;
    }

    /** Becomes a JSON "problem details" reply, like the API's other errors, with a "code" the app reads. */
    static final class Blocked extends ErrorResponseException {
        Blocked(AccessService.Block block) {
            super(statusFor(block), problemFor(block), null);
        }

        private static HttpStatus statusFor(AccessService.Block block) {
            return block == AccessService.Block.PHONE_REQUIRED ? HttpStatus.PRECONDITION_REQUIRED
                    : HttpStatus.PAYMENT_REQUIRED;
        }

        private static ProblemDetail problemFor(AccessService.Block block) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(statusFor(block),
                    block == AccessService.Block.PHONE_REQUIRED
                            ? "Confirm your mobile number to continue."
                            : "A Koshly subscription is needed to continue.");
            problem.setProperty("code", block.name());
            return problem;
        }
    }
}
