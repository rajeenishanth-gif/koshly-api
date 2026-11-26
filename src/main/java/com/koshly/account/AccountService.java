package com.koshly.account;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
    private final Clock clock = Clock.systemUTC();

    public AccountService(AccountRepository accounts, UserService users) {
        this.accounts = accounts;
        this.users = users;
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
        if (accounts.countByUserIdAndType(jwt.getSubject(), request.type()) >= MAX_PER_TYPE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You can add up to " + MAX_PER_TYPE + " of each kind of account.");
        }
        Account account = new Account(jwt.getSubject(), request.type());
        account.apply(request);
        return AccountResponse.from(accounts.save(account), LocalDate.now(clock));
    }

    @Transactional
    public AccountResponse update(Jwt jwt, UUID id, AccountRequest request) {
        Account account = find(jwt, id);
        if (!account.getType().equals(request.type())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "An account's type can't be changed. Delete it and add a new one.");
        }
        account.apply(request);
        // No save() needed: JPA writes changes to a loaded entity when the transaction ends.
        accounts.flush();
        return AccountResponse.from(account, LocalDate.now(clock));
    }

    @Transactional
    public void delete(Jwt jwt, UUID id) {
        accounts.delete(find(jwt, id));
    }

    private Account find(Jwt jwt, UUID id) {
        return accounts.findForUser(id, jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
    }
}
