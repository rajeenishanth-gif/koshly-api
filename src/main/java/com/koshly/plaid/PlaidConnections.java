package com.koshly.plaid;

import com.koshly.account.Account;

/**
 * Talks to Plaid about linked accounts. AccountService calls this when a user
 * removes an account, so that Koshly stops receiving its data right away.
 */
public interface PlaidConnections {

    /**
     * Stops syncing a Plaid-linked account. One Plaid "Item" (one bank login)
     * can hold several accounts, so the plan is: if other accounts from the
     * same login are still in use, only stop saving this account's data;
     * once none are left, call Plaid's /item/remove so the access token is
     * revoked and Plaid stops sending updates.
     */
    void accountRemoved(Account account);
}
