package com.koshly.banklink;

import java.time.Instant;
import java.util.Map;

/**
 * A service that links bank accounts in a country: Plaid in the US, an
 * Account Aggregator in India. Market.bankLinkProvider() says which one each
 * country uses; BankDataProviders picks it. The screens and the rest of the
 * API don't know which provider is behind a link.
 *
 * Flow:
 *   1. start()     the app gets what it needs to open the provider's screen
 *                  (Plaid: a link token; Account Aggregator: a consent URL)
 *   2. complete()  after the user approves, save the link and fetch accounts
 *   3. refresh()   fetch new balances and transactions
 *   4. disconnect() revoke access (Plaid /item/remove, or revoke the AA consent)
 */
public interface BankDataProvider {

    /** Stored in bank_link.provider and account.source, e.g. "plaid". */
    String id();

    /** Shown to users, e.g. "Plaid". */
    String displayName();

    /** False until the integration is built and its keys are configured. */
    boolean isAvailable();

    /** Begins linking for a new, pending bank link. */
    LinkStart start(BankLink link);

    /**
     * Finishes linking with what the provider's screen returned (Plaid: a
     * public token; Account Aggregator: the consent ID and status), then
     * creates the accounts.
     */
    void complete(BankLink link, Map<String, String> result);

    /** Fetches new balances and transactions for an active link. */
    void refresh(BankLink link);

    /** Revokes Koshly's access at the provider. Must be safe to call twice. */
    void disconnect(BankLink link);

    /**
     * What the app needs to open the provider's screen.
     *
     * @param flow        "plaid_link" (open Plaid Link with the token) or
     *                    "aa_consent" (open the redirect URL)
     * @param token       Plaid link token, or null
     * @param redirectUrl Account Aggregator consent page, or null
     */
    record LinkStart(String linkId, String provider, String flow, String token, String redirectUrl, Instant expiresAt) {
    }
}
