package com.koshly.banklink;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.koshly.access.AccessService;
import com.koshly.account.Account;
import com.koshly.region.AccountCountry;
import com.koshly.region.Market;
import com.koshly.user.UserService;

/**
 * Bank connections. Picks the provider from the account's country (from its sign-in,
 * app), so the same calls work for Plaid in the US and Account Aggregator in India.
 */
@Service
public class BankLinkService {

    private final BankLinkRepository links;
    private final BankDataProviders providers;
    private final UserService users;
    private final Clock clock = Clock.systemUTC();

    private final AccountCountry accountCountry;
    private final AccessService access;

    public BankLinkService(BankLinkRepository links, BankDataProviders providers, UserService users,
            AccountCountry accountCountry, AccessService access) {
        this.accountCountry = accountCountry;
        this.access = access;
        this.links = links;
        this.providers = providers;
        this.users = users;
    }

    /** Which provider the account's country uses and whether it's ready. */
    public ProviderInfo providerFor(Jwt jwt) {
        Market market = accountCountry.of(jwt);
        BankDataProvider provider = providers.forCountry(market);
        return new ProviderInfo(market.country(), provider.id(), provider.displayName(), provider.isAvailable());
    }

    /** Starts linking a bank in the account's country. 503 while that country's provider isn't ready. */
    @Transactional
    public BankDataProvider.LinkStart start(Jwt jwt) {
        Market market = accountCountry.of(jwt);
        BankDataProvider provider = providers.forCountry(market);
        if (!provider.isAvailable()) {
            throw notAvailable(provider.displayName());
        }
        access.requirePaidPlan(jwt.getSubject()); // 402: bank linking is for paid plans
        users.ensureUserExists(jwt);
        BankLink link = links.save(new BankLink(jwt.getSubject(), market.country(), provider.id()));
        return provider.start(link);
    }

    @Transactional(readOnly = true)
    public List<BankLinkResponse> list(Jwt jwt) {
        return links.findAllForUser(jwt.getSubject(), accountCountry.of(jwt).country()).stream()
                .map(BankLinkResponse::from)
                .toList();
    }

    /** Disconnects a bank: revokes access at the provider. Its accounts are removed by the caller. */
    @Transactional
    public BankLink disconnect(Jwt jwt, UUID id) {
        BankLink link = links.findForUser(id, jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bank connection not found."));
        revoke(link);
        return link;
    }

    /** Account deletion: revokes access at the provider for every bank connection still active. */
    @Transactional
    public void disconnectAll(Jwt jwt) {
        links.findAllForUser(jwt.getSubject(), accountCountry.of(jwt).country()).stream()
                .filter(l -> !BankLink.REVOKED.equals(l.getStatus()))
                .forEach(this::revoke);
    }

    /**
     * Called when the user removes a linked account. One bank login can hold
     * several accounts, so access is revoked only when none of them are left.
     */
    @Transactional
    public void accountRemoved(Account account, long otherAccountsStillInUse) {
        if (account.getBankLinkId() == null || otherAccountsStillInUse > 0) {
            return;
        }
        links.findForUser(account.getBankLinkId(), account.getUserId()).ifPresent(this::revoke);
    }

    private void revoke(BankLink link) {
        providers.forLink(link).disconnect(link);
        link.markRevoked(Instant.now(clock));
    }

    static ResponseStatusException notAvailable(String providerName) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Bank linking with " + providerName + " isn't available yet. You can add accounts yourself for now.");
    }

    /** What the app needs to show the right "Connect your bank" option. */
    public record ProviderInfo(String country, String provider, String name, boolean available) {
    }
}
