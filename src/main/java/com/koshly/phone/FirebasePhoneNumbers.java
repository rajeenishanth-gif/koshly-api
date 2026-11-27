package com.koshly.phone;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.koshly.config.FirebaseAdmin;

/** Sets the phone number on the Firebase user with the Admin SDK. */
@Component
class FirebasePhoneNumbers implements PhoneNumbers {

    private final FirebaseAdmin admin;

    FirebasePhoneNumbers(FirebaseAdmin admin) {
        this.admin = admin;
    }

    @Override
    public void markVerified(String uid, String tenantId, String phone) {
        try {
            admin.auth(tenantId).updateUser(new UserRecord.UpdateRequest(uid).setPhoneNumber(phone));
        } catch (FirebaseAuthException e) {
            // Firebase keeps phone numbers unique, so a number can be verified on one account only.
            if (e.getAuthErrorCode() == AuthErrorCode.PHONE_NUMBER_ALREADY_EXISTS) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This number is already verified on another Koshly account.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Couldn't save the number. Please try again.", e);
        }
    }
}
