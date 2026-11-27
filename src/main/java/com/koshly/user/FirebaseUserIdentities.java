package com.koshly.user;

import java.util.concurrent.ExecutionException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuthException;
import com.koshly.config.FirebaseAdmin;

/**
 * Deletes users/{uid} (with anything under it), the usernames/{name} entry that
 * points to it, and the Firebase user. Admin rights skip the Firestore rules.
 */
@Component
class FirebaseUserIdentities implements UserIdentities {

    private final FirebaseAdmin admin;

    FirebaseUserIdentities(FirebaseAdmin admin) {
        this.admin = admin;
    }

    @Override
    public void delete(String uid, String tenantId) {
        try {
            Firestore db = admin.firestore();
            DocumentReference profile = db.collection("users").document(uid);
            DocumentSnapshot snap = profile.get().get();
            String username = snap.exists() ? snap.getString("usernameLower") : null;
            if (username != null && !username.isBlank()) {
                DocumentReference name = db.collection("usernames").document(username);
                DocumentSnapshot nameSnap = name.get().get();
                // Only free the name if it really is this person's.
                if (nameSnap.exists() && uid.equals(nameSnap.getString("uid"))) {
                    name.delete().get();
                }
            }
            db.recursiveDelete(profile).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable(e);
        } catch (ExecutionException e) {
            throw unavailable(e);
        }
        try {
            admin.auth(tenantId).deleteUser(uid);
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() != AuthErrorCode.USER_NOT_FOUND) {
                throw unavailable(e);
            }
        }
    }

    private static ResponseStatusException unavailable(Exception e) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Couldn't delete your account right now. Nothing was removed. Please try again.", e);
    }
}
