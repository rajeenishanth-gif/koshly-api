package com.koshly.testsupport;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.Map;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Signed-in test users: US accounts (no tenant) and Indian accounts (the India tenant). */
public final class TestUsers {

    /** The India tenant ID that @KoshlyTest configures. */
    public static final String INDIA_TENANT = "india-test";

    /** The Canada tenant ID that @KoshlyTest configures. */
    public static final String CANADA_TENANT = "canada-test";

    private TestUsers() {
    }

    /** A verified user of a US account (signed in without a tenant, so the default country). */
    public static RequestPostProcessor user(String uid) {
        return jwt().jwt(token -> token.subject(uid)
                .claim("email", uid + "@example.com")
                .claim("email_verified", true));
    }

    /** A verified user of an Indian account (signed in through the India tenant). */
    public static RequestPostProcessor indian(String uid) {
        return tenant(uid, INDIA_TENANT);
    }

    /** A verified user of a Canadian account (signed in through the Canada tenant). */
    public static RequestPostProcessor canadian(String uid) {
        return tenant(uid, CANADA_TENANT);
    }

    /** A verified user signed in through any tenant, known to Koshly or not. */
    public static RequestPostProcessor tenant(String uid, String tenant) {
        return jwt().jwt(token -> token.subject(uid)
                .claim("email", uid + "@example.com")
                .claim("email_verified", true)
                .claim("firebase", Map.of("tenant", tenant)));
    }
}
