package com.koshly.user;

/** Removes a person from Firebase: their Firestore profile and username, then their sign-in. */
public interface UserIdentities {

    /** @param tenantId the tenant they signed in through, or null for the default */
    void delete(String uid, String tenantId);
}
