package com.koshly.phone;

/**
 * Records a verified mobile number on the user's Firebase account. From then
 * on their sign-in token carries it ("phone_number"), which is how the app and
 * the API know the number is verified.
 */
public interface PhoneNumbers {

    /**
     * @param tenantId the tenant the user signed in through, or null for the default
     * @throws org.springframework.web.server.ResponseStatusException 409 when another
     *         account already has this number verified
     */
    void markVerified(String uid, String tenantId, String phone);
}
