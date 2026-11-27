package com.koshly.accountlink;

/**
 * Signs a person in to their account in another country's Firebase tenant,
 * creating it the first time. Called only after they've confirmed a code sent
 * to that email or phone, which proves the address is theirs.
 */
public interface TenantAccounts {

    /**
     * @return a one-time Firebase custom token for that tenant's user; the app
     *         signs in with it (signInWithCustomToken) on that country's sign-in
     */
    String signInTokenFor(String tenantId, Channel channel, String destination);
}
