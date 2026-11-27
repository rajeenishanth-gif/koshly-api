package com.koshly.accountlink;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * koshly.codes.sender=disabled: codes are switched off. Asking for one answers
 * 503 "not available yet". For a deployed API before texts are set up (the
 * development sender refuses to run on Cloud Run), so the rest of the API can
 * go live. Phone verification is "verify later", so nothing is blocked.
 */
@Component
@ConditionalOnProperty(name = "koshly.codes.sender", havingValue = "disabled")
class DisabledCodeSender implements CodeSender {

    @Override
    public void send(Channel channel, String destination, String code) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Verification codes aren't available yet. Please try again later.");
    }
}
