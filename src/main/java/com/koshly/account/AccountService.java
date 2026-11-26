package com.koshly.account;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.koshly.config.KoshlyProperties;
import com.koshly.banklink.BankLinkService;
import com.koshly.region.Market;
import com.koshly.user.UserService;

/**
 * Business rules for credit cards, bank accounts and loans. Every method takes
 * the signed-in user's token and only ever touches that user's accounts:
 * anyone else's account is simply "not found".
 */
@Service
public class AccountService {

    static final int MAX_PER_TYPE = 50;

    private final AccountRepository accounts;
    private final UserService users;
    private final BankLinkService bankLinks;
    private final Duration keepRemovedFor;
    private final Clock clock = Clock.systemUTC();

    public AccountService(AccountRepository accounts, UserService users, BankLinkService bankLinks,
            KoshlyProperties properties) {
        this.accounts = accounts;
        this.users = users;
        this.bankLinks = bankLinks;
        this.keepRemovedFor = Duration.ofDays(properties.retention().removedAccountDays());
    }

    /** One country's accounts: the given one, or the user's current country. */
    @Transactional(readOnly = true)
    public List<AccountResponse> list(Jwt jwt, String country) {
        LocalDate today = today(jwt);
        String chosen = country != null ? market(country).country() : users.settingsFor(jwt).country();
        return accounts.findAllForUser(jwt.getSubject(), chosen).stream()
                .map(account -> AccountResponse.from(account, today))
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(Jwt jwt, UUID id) {
        return AccountResponse.from(find(jwt, id), today(jwt));
    }

    @Transactional
    public AccountResponse create(Jwt jwt, AccountRequest request) {
        users.ensureUserExists(jwt);
        checkRoomFor(jwt, request.type());
        Account account = new Account(jwt.getSubject(), request.type());
        String country = request.country() != null ? request.country() : users.settingsFor(jwt).country();
        account.apply(request, market(country));
        return AccountResponse.from(accounts.save(account), today(jwt));
    }

    @Transactional
    public AccountResponse update(Jwt jwt, UUID id, AccountRequest request) {
        Account account = find(jwt, id);
        if (!account.getType().equals(request.type())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "An account's type can't be changed. Remove it and add a new one.");
        }
        if (request.country() != null && !request.country().equals(account.getCountry())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "An account can't move to another country. Remove it and add it in the other country.");
        }
        account.apply(request, market(account.getCountry()));
        // No save() needed: JPA writes changes to a loaded entity when the transaction ends.
        accounts.flush();
        return AccountResponse.from(account, today(jwt));
    }

    /**
     * Removes an account: it disappears from the app at once and is deleted for
     * good after the retention period (see {@link #deleteExpiredRemovals}).
     * A linked account (Plaid, Account Aggregator) is also disconnected straight away.
     */
    @Transactional
    public RemovedAccountResponse remove(Jwt jwt, UUID id) {
        Account account = find(jwt, id);
        Instant now = Instant.now(clock);
        account.markRemoved(now);
        accounts.flush(); // write removed_at now, before anything else reads the table
        if (account.isLinked()) {
            // Revokes the provider's access once no account from that bank login is left.
            bankLinks.accountRemoved(account, accounts.countByBankLinkIdAndRemovedAtIsNull(account.getBankLinkId()));
        }
        return new RemovedAccountResponse(account.getId(), account.getName(), now,
                now.plus(keepRemovedFor), !account.isLinked());
    }

    /** Removes every account that came from a bank connection being disconnected. */
    @Transactional
    public void removeAllFromLink(Jwt jwt, UUID bankLinkId) {
        Instant now = Instant.now(clock);
        accounts.findAllByUserIdAndBankLinkIdAndRemovedAtIsNull(jwt.getSubject(), bankLinkId)
                .forEach(a -> a.markRemoved(now));
        accounts.flush();
    }

    /** Undo for {@link #remove}, while the account hasn't been deleted for good yet. */
    @Transactional
    public AccountResponse restore(Jwt jwt, UUID id) {
        Account account = accounts.findRemovedForUser(id, jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
        if (account.isLinked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This account was disconnected from your bank. Link it again to bring it back.");
        }
        checkRoomFor(jwt, account.getType());
        account.restore();
        accounts.flush();
        return AccountResponse.from(account, today(jwt));
    }

    /** Deletes, for good, accounts removed longer ago than the retention period. Runs nightly. */
    @Transactional
    public int deleteExpiredRemovals() {
        return accounts.deleteRemovedBefore(Instant.now(clock).minus(keepRemovedFor));
    }

    private static Market market(String country) {
        return Market.forCountry(country).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Koshly isn't available in that country yet."));
    }

    /** Today's date where the user lives, so "due today" matches their calendar. */
    private LocalDate today(Jwt jwt) {
        return LocalDate.now(clock.withZone(users.settingsFor(jwt).timeZone()));
    }

    private void checkRoomFor(Jwt jwt, String type) {
        if (accounts.countByUserIdAndTypeAndRemovedAtIsNull(jwt.getSubject(), type) >= MAX_PER_TYPE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You can add up to " + MAX_PER_TYPE + " of each kind of account.");
        }
    }

    private Account find(Jwt jwt, UUID id) {
        return accounts.findForUser(id, jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
    }
}
