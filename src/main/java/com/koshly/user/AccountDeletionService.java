package com.koshly.user;

import java.time.Duration;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.koshly.accountlink.CountryLinkRepository;
import com.koshly.banklink.BankLinkService;
import com.koshly.config.SessionCheck;
import com.koshly.region.AccountCountry;

/**
 * "Delete my account": everything Koshly holds about the person, for good.
 * Required by Apple's App Store rules and by privacy laws such as CCPA.
 *
 * <p>Order: bank connections are disconnected at the provider, then the
 * app_user row is deleted (the database deletes their cards, accounts, loans,
 * bank links and codes with it, ON DELETE CASCADE), then Firebase. All in one
 * transaction: if Firebase fails, the database delete is rolled back, so the
 * person can simply try again.
 */
@Service
public class AccountDeletionService {

    /** Must have signed in this recently to delete the account. */
    static final Duration RECENT_SIGN_IN = Duration.ofMinutes(15);

    private final AppUserRepository users;
    private final BankLinkService bankLinks;
    private final UserIdentities identities;
    private final SessionCheck sessions;
    private final CountryLinkRepository countryLinks;

    public AccountDeletionService(AppUserRepository users, BankLinkService bankLinks, UserIdentities identities,
            SessionCheck sessions, CountryLinkRepository countryLinks) {
        this.countryLinks = countryLinks;
        this.users = users;
        this.bankLinks = bankLinks;
        this.identities = identities;
        this.sessions = sessions;
    }

    @Transactional
    public void deleteCurrentUser(Jwt jwt) {
        SessionCheck.requireRecentSignIn(jwt, RECENT_SIGN_IN,
                "For your safety, sign in again, then delete your account.");
        sessions.requireActive(jwt);

        bankLinks.disconnectAll(jwt);
        // An added country's link to its home (a home account's own links go with it by cascade;
        // its added accounts stay, each with its own data, and can be deleted from inside them).
        countryLinks.deleteByLinkedUserId(jwt.getSubject());
        users.deleteByFirebaseUid(jwt.getSubject());
        identities.delete(jwt.getSubject(), AccountCountry.tenantOf(jwt).orElse(null));
    }
}
