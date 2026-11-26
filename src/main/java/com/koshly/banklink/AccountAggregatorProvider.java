package com.koshly.banklink;

import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * India bank linking through the RBI's Account Aggregator network. Not built
 * yet: isAvailable() is false. Koshly will need to be a registered Financial
 * Information User (FIU), usually through a partner such as Setu or Finvu,
 * whose API decides the exact calls:
 *   start()      -> create a consent request; return the consent page URL
 *   complete()   -> on the consent-approved callback, store the consent handle
 *                   and expiry, then fetch the financial information
 *   refresh()    -> fetch again while the consent is valid; ask for a new one when it expires
 *   disconnect() -> revoke the consent
 */
@Component
class AccountAggregatorProvider implements BankDataProvider {

    static final String ID = "account_aggregator";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Account Aggregator";
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
