package com.koshly.config;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.AbstractFirebaseAuth;
import com.google.firebase.auth.FirebaseAuth;

/**
 * The Firebase Admin SDK, for changing Firebase users from the server (create
 * a user in another country, set a verified phone number).
 *
 * <p>Credentials come from Google's "application default credentials": on Cloud
 * Run, the service's own account (it needs the "Service Account Token Creator"
 * role to sign tokens and "Firebase Authentication Admin" to update users); on
 * your PC, a service account key file named by GOOGLE_APPLICATION_CREDENTIALS.
 * Started on first use, so the API still starts without credentials.
 */
@Component
public class FirebaseAdmin {

    private static final String APP_NAME = "koshly-admin";

    private final String projectId;

    public FirebaseAdmin(KoshlyProperties properties) {
        this.projectId = properties.firebase().projectId();
    }

    /** Users of the default (no-tenant) sign-in. */
    public FirebaseAuth auth() {
        return FirebaseAuth.getInstance(app());
    }

    /** Users of one tenant (one country's separate accounts), or the default when tenantId is null. */
    public AbstractFirebaseAuth auth(String tenantId) {
        return tenantId == null ? auth() : auth().getTenantManager().getAuthForTenant(tenantId);
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
                    "This isn't set up on the server yet (Firebase Admin credentials are missing).", e);
        }
    }
}
