package com.koshly.ratelimit;

import java.util.Map;
import java.util.Set;

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
 * Runs before every /api request, after Spring Security has checked the sign-in, and
 * answers 429 Too Many Requests (with a Retry-After header) when the person is over
 * their limit. Requests without a valid sign-in never get here: they're already 401.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Set<String> READS = Set.of("GET", "HEAD", "OPTIONS");

    private final RateLimiter reads;
    private final RateLimiter writes;

    public RateLimitInterceptor(RateLimiter reads, RateLimiter writes) {
        this.reads = reads;
        this.writes = writes;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken token)) return true;

        long waitSeconds = (READS.contains(request.getMethod()) ? reads : writes).tryAcquire(personKey(token));
        if (waitSeconds > 0) throw new TooManyRequests(waitSeconds);
        return true;
    }

    /** The Firebase user ID, with the tenant (country) it signed in through: IDs are per tenant. */
    private static String personKey(JwtAuthenticationToken token) {
        Object firebase = token.getToken().getClaims().get("firebase");
        Object tenant = firebase instanceof Map<?, ?> claims ? claims.get("tenant") : null;
        return (tenant == null ? "" : tenant + "/") + token.getToken().getSubject();
    }

    /** Becomes a JSON "problem details" 429, like the API's other errors. */
    static final class TooManyRequests extends ErrorResponseException {
        TooManyRequests(long waitSeconds) {
            super(HttpStatus.TOO_MANY_REQUESTS, ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests. Please wait a moment and try again."), null);
            getHeaders().set("Retry-After", Long.toString(waitSeconds));
        }
    }
}
