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
import com.koshly.plaid.PlaidConnections;
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
    private final PlaidConnections plaid;
    private final Duration keepRemovedFor;
    private final Clock clock = Clock.systemUTC();

    public AccountService(AccountRepository accounts, UserService users, PlaidConnections plaid,
            KoshlyProperties properties) {
        this.accounts = accounts;
        this.users = users;
        this.plaid = plaid;
        this.keepRemovedFor = Duration.ofDays(properties.retention().removedAccountDays());
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(Jwt jwt) {
        LocalDate today = LocalDate.now(clock);
        return accounts.findAllForUser(jwt.getSubject()).stream()
                .map(account -> AccountResponse.from(account, today))
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(Jwt jwt, UUID id) {
        return AccountResponse.from(find(jwt, id), LocalDate.now(clock));
    }

    @Transactional
    public AccountResponse create(Jwt jwt, AccountRequest request) {
        users.ensureUserExists(jwt);
        checkRoomFor(jwt, request.type());
        Account account = new Account(jwt.getSubject(), request.type());
        account.apply(request);
        return AccountResponse.from(accounts.save(account), LocalDate.now(clock));
    }

    @Transactional
    public AccountResponse update(Jwt jwt, UUID id, AccountRequest request) {
        Account account = find(jwt, id);
        if (!account.getType().equals(request.type())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "An account's type can't be changed. Remove it and add a new one.");
        }
        account.apply(request);
        // No save() needed: JPA writes changes to a loaded entity when the transaction ends.
        accounts.flush();
        return AccountResponse.from(account, LocalDate.now(clock));
    }

    /**
     * Removes an account: it disappears from the app at once and is deleted for
     * good after the retention period (see {@link #deleteExpiredRemovals}).
     * A Plaid-linked account is also disconnected from Plaid straight away.
     */
    @Transactional
    public RemovedAccountResponse remove(Jwt jwt, UUID id) {
        Account account = find(jwt, id);
        Instant now = Instant.now(clock);
        account.markRemoved(now);
        accounts.flush(); // write removed_at now, before anything else reads the table
        if (account.isLinked()) {
            plaid.accountRemoved(account);
        }
        return new RemovedAccountResponse(account.getId(), account.getName(), now,
                now.plus(keepRemovedFor), !account.isLinked());
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
        return AccountResponse.from(account, LocalDate.now(clock));
    }

    /** Deletes, for good, accounts removed longer ago than the retention period. Runs nightly. */
    @Transactional
    public int deleteExpiredRemovals() {
        return accounts.deleteRemovedBefore(Instant.now(clock).minus(keepRemovedFor));
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
