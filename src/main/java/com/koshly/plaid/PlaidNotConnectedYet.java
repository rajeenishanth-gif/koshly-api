package com.koshly.plaid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.koshly.account.Account;

/**
 * Stand-in until the Plaid integration exists: there are no linked accounts
 * yet, so there is nothing to disconnect. Replace it with a class that calls
 * Plaid (see PlaidConnections) in the Plaid step.
 */
@Component
class PlaidNotConnectedYet implements PlaidConnections {

    private static final Logger log = LoggerFactory.getLogger(PlaidNotConnectedYet.class);

    @Override
    public void accountRemoved(Account account) {
        log.warn("Plaid isn't set up yet, so linked account {} wasn't disconnected from Plaid.", account.getId());
    }
}
