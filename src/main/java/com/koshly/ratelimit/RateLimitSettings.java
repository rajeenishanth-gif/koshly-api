package com.koshly.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

/**
 * Settings under "koshly.rate-limit." in application.properties: how many API
 * requests one signed-in person may make per minute before getting 429.
 *
 * @param readsPerMinute  GET requests (loading screens). Generous: one screen can make several.
 * @param writesPerMinute POST, PUT, PATCH and DELETE requests (adding, changing, removing).
 *                        Lower: a person adds a handful of things a minute, a bot thousands.
 */
@Validated
@ConfigurationProperties(prefix = "koshly.rate-limit")
public record RateLimitSettings(@Min(1) int readsPerMinute, @Min(1) int writesPerMinute) {
}
