package com.koshly.accountlink;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.google.firebase.auth.AbstractFirebaseAuth;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.koshly.config.FirebaseAdmin;

/** Finds, creates and signs in users in other countries' tenants (see {@link FirebaseAdmin}). */
@Component
class FirebaseTenantAccounts implements TenantAccounts {

    private final FirebaseAdmin admin;

    FirebaseTenantAccounts(FirebaseAdmin admin) {
        this.admin = admin;
    }

    @Override
    public TenantSignIn signInTokenFor(String tenantId, Channel channel, String destination) {
        AbstractFirebaseAuth auth = tenant(tenantId);
        try {
            UserRecord user = findOrCreate(auth, channel, destination);
            return new TenantSignIn(user.getUid(), auth.createCustomToken(user.getUid()));
        } catch (FirebaseAuthException e) {
            throw failed(e);
        }
    }

    @Override
    public Optional<String> findByEmail(String tenantId, String email) {
        try {
            return Optional.of(tenant(tenantId).getUserByEmail(email).getUid());
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND) {
                return Optional.empty();
            }
            throw failed(e);
        }
    }

    @Override
    public String create(String tenantId, String email, boolean emailVerified) {
        UserRecord.CreateRequest create = new UserRecord.CreateRequest();
        if (email != null && !email.isBlank()) {
            create.setEmail(email).setEmailVerified(emailVerified);
        }
        try {
            return tenant(tenantId).createUser(create).getUid();
        } catch (FirebaseAuthException e) {
            throw failed(e);
        }
    }

    @Override
    public String signInToken(String tenantId, String uid) {
        try {
            return tenant(tenantId).createCustomToken(uid);
        } catch (FirebaseAuthException e) {
            throw failed(e);
        }
    }

    /** That tenant's users, or the default country's (no tenant) when tenantId is null. */
    private AbstractFirebaseAuth tenant(String tenantId) {
        return admin.auth(tenantId);
    }

    private static ResponseStatusException failed(FirebaseAuthException e) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Couldn't open that account. Please try again.", e);
    }

    private static UserRecord findOrCreate(AbstractFirebaseAuth auth, Channel channel, String destination)
            throws FirebaseAuthException {
        try {
            return channel == Channel.EMAIL ? auth.getUserByEmail(destination) : auth.getUserByPhoneNumber(destination);
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() != AuthErrorCode.USER_NOT_FOUND) {
                throw e;
            }
            UserRecord.CreateRequest create = new UserRecord.CreateRequest();
            if (channel == Channel.EMAIL) {
                create.setEmail(destination).setEmailVerified(true); // just confirmed by the code
            } else {
                create.setPhoneNumber(destination); // just confirmed by the code
            }
            return auth.createUser(create);
        }
    }
}
