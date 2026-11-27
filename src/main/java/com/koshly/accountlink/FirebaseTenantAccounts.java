package com.koshly.accountlink;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.multitenancy.TenantAwareFirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.koshly.config.KoshlyProperties;

/**
 * Uses the Firebase Admin SDK. Its credentials come from Google's "application
 * default credentials": on Cloud Run, the service's own account (it needs the
 * "Service Account Token Creator" role to sign tokens); on your PC, a service
 * account key file named by GOOGLE_APPLICATION_CREDENTIALS. Started on first
 * use, so the API still starts without credentials.
 */
@Component
class FirebaseTenantAccounts implements TenantAccounts {

    private static final String APP_NAME = "koshly-admin";

    private final String projectId;

    FirebaseTenantAccounts(KoshlyProperties properties) {
        this.projectId = properties.firebase().projectId();
    }

    @Override
    public String signInTokenFor(String tenantId, Channel channel, String destination) {
        TenantAwareFirebaseAuth auth = FirebaseAuth.getInstance(app()).getTenantManager().getAuthForTenant(tenantId);
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

    private synchronized FirebaseApp app() {
        for (FirebaseApp existing : FirebaseApp.getApps()) {
            if (existing.getName().equals(APP_NAME)) {
                return existing;
            }
        }
        try {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .setProjectId(projectId)
                    .build();
            return FirebaseApp.initializeApp(options, APP_NAME);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Adding a country isn't set up on this server yet.", e);
        }
    }
}
