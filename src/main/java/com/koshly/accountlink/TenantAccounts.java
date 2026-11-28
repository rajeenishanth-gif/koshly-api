package com.koshly.accountlink;

import java.util.Optional;

/**
 * The person's accounts in other countries' Firebase tenants: find, create, and
 * sign in to them. A null tenant ID means the default country's sign-ins (no tenant:
 * the US until tenants are set up). The app signs in with the returned one-time custom token
 * (signInWithCustomToken) on that country's sign-in.
 */
public interface TenantAccounts {

    /**
     * Finds or creates the user with that email or phone and signs them in. Called only
     * after they've confirmed a code sent there, which proves the address is theirs.
     */
    TenantSignIn signInTokenFor(String tenantId, Channel channel, String destination);

    /** The user ID of an existing account with that email in the tenant, if there is one. */
    Optional<String> findByEmail(String tenantId, String email);

    /**
     * Creates a user in the tenant for someone already signed in to their home account.
     * The email (if any) is copied from the home sign-in, verified only if it was there.
     *
     * @return the new user's ID
     */
    String create(String tenantId, String email, boolean emailVerified);

    /** A one-time sign-in token for an existing user of the tenant. */
    String signInToken(String tenantId, String uid);

    /** The account signed in to, and the token the app signs in with. */
    record TenantSignIn(String uid, String token) {
    }
}
