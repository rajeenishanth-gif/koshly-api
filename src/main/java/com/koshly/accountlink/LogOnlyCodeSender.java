package com.koshly.accountlink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Development stand-in: prints the code in the API's console instead of
 * sending it, so codes can be tried without an email or SMS service.
 *
 * <p>Used only when koshly.codes.sender=log (the local default), and it refuses
 * to start in production: anyone who can read production logs could use the
 * codes, and real users would never receive them.
 */
@Component
@ConditionalOnProperty(name = "koshly.codes.sender", havingValue = "log", matchIfMissing = true)
class LogOnlyCodeSender implements CodeSender {

    private static final Logger log = LoggerFactory.getLogger(LogOnlyCodeSender.class);

    LogOnlyCodeSender(@Value("${koshly.environment:development}") String environment) {
        refuseInProduction(environment, System.getenv("K_SERVICE"));
    }

    /**
     * @param cloudRunService Cloud Run sets K_SERVICE on every service, so a deployed API
     *                        is caught even if KOSHLY_ENVIRONMENT was forgotten
     */
    static void refuseInProduction(String environment, String cloudRunService) {
        if ("production".equalsIgnoreCase(environment) || (cloudRunService != null && !cloudRunService.isBlank())) {
            throw new IllegalStateException("The development code sender (koshly.codes.sender=log) can't run in "
                    + "production: it would write codes to the logs and send nothing. Set KOSHLY_CODE_SENDER=twilio "
                    + "and the TWILIO_* settings.");
        }
    }

    @Override
    public void send(Channel channel, String destination, String code) {
        log.warn("DEV ONLY - verification code for {} {}: {}", channel.value(), destination, code);
    }
}
