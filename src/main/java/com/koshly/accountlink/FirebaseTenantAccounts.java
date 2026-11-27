package com.koshly.accountlink;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.auth.multitenancy.TenantAwareFirebaseAuth;
import com.koshly.config.FirebaseAdmin;

/** Finds or creates the user in another country's tenant and signs them in (see {@link FirebaseAdmin}). */
@Component
class FirebaseTenantAccounts implements TenantAccounts {

    private final FirebaseAdmin admin;

    FirebaseTenantAccounts(FirebaseAdmin admin) {
        this.admin = admin;
    }

    @Override
    public String signInTokenFor(String tenantId, Channel channel, String destination) {
        TenantAwareFirebaseAuth auth = admin.auth().getTenantManager().getAuthForTenant(tenantId);
        try {
            UserRecord user = findOrCreate(auth, channel, destination);
            return auth.createCustomToken(user.getUid());
        } catch (FirebaseAuthException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Couldn't open that account. Please try again.", e);
        }
    }

    private static UserRecord findOrCreate(TenantAwareFirebaseAuth auth, Channel channel, String destination)
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
