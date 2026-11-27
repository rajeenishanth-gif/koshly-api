package com.koshly.phone;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.koshly.accountlink.AccountLinkService;
import com.koshly.accountlink.Channel;
import com.koshly.accountlink.CodePurpose;
import com.koshly.accountlink.OneTimeCodes;
import com.koshly.accountlink.VerificationCode;
import com.koshly.config.SessionCheck;
import com.koshly.region.AccountCountry;
import com.koshly.user.UserService;

/**
 * Verifies the mobile number on the signed-in user's own account: a 6-digit
 * code by text message, then the number is saved on their Firebase user.
 */
@Service
public class PhoneVerificationService {

    private final OneTimeCodes codes;
    private final PhoneNumbers phones;
    private final AccountCountry accountCountry;
    private final UserService users;
    private final SessionCheck sessions;

    public PhoneVerificationService(OneTimeCodes codes, PhoneNumbers phones, AccountCountry accountCountry,
            UserService users, SessionCheck sessions) {
        this.sessions = sessions;
        this.codes = codes;
        this.phones = phones;
        this.accountCountry = accountCountry;
        this.users = users;
    }

    @Transactional
    public OneTimeCodes.CodeSent sendCode(Jwt jwt, String rawPhone) {
        String phone = AccountLinkService.normalizePhone(rawPhone);
        String country = accountCountry.of(jwt).country();
        sessions.requireActive(jwt);
        users.ensureUserExists(jwt);
        return codes.issue(jwt.getSubject(), country, CodePurpose.PHONE, Channel.SMS, phone);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class) // a wrong try still counts
    public Verified verify(Jwt jwt, UUID id, String code) {
        sessions.requireActive(jwt);
        VerificationCode vc = codes.use(jwt.getSubject(), id, CodePurpose.PHONE, code);
        phones.markVerified(jwt.getSubject(), AccountCountry.tenantOf(jwt).orElse(null), vc.getDestination());
        return new Verified(vc.getDestination());
    }

    /** The app refreshes its sign-in after this, so the token carries the number. */
    public record Verified(String phone) {
    }
}
