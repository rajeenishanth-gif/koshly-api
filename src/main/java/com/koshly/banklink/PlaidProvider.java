package com.koshly.banklink;

import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * US bank linking through Plaid. Not built yet: isAvailable() is false, so the
 * app shows "coming soon". To build it: add the Plaid Java client, keep the
 * client ID and secret in Secret Manager, then
 *   start()      -> /link/token/create
 *   complete()   -> /item/public_token/exchange, encrypt and store the access token,
 *                   then /accounts/get and /liabilities/get to create the accounts
 *   refresh()    -> /transactions/sync (triggered by Plaid webhooks via Pub/Sub)
 *   disconnect() -> /item/remove
 */
@Component
class PlaidProvider implements BankDataProvider {

    static final String ID = "plaid";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Plaid";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public LinkStart start(BankLink link) {
        throw BankLinkService.notAvailable(displayName());
    }

    @Override
    public void complete(BankLink link, Map<String, String> result) {
        throw BankLinkService.notAvailable(displayName());
    }

    @Override
    public void refresh(BankLink link) {
        // nothing to refresh until linking exists
    }

    @Override
    public void disconnect(BankLink link) {
        // nothing to revoke until linking exists
    }
}
