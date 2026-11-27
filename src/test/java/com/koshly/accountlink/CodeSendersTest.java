package com.koshly.accountlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The development sender stays out of production; the Twilio sender needs its settings. */
class CodeSendersTest {

    @Test
    void theDevelopmentSenderRefusesProduction() {
        assertThatThrownBy(() -> LogOnlyCodeSender.refuseInProduction("production", null))
                .isInstanceOf(IllegalStateException.class);
        // Any Cloud Run service counts as production, even if KOSHLY_ENVIRONMENT was forgotten.
        assertThatThrownBy(() -> LogOnlyCodeSender.refuseInProduction("development", "koshly-api"))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> LogOnlyCodeSender.refuseInProduction("development", null)).doesNotThrowAnyException();
    }

    @Test
    void twilioNeedsItsSettings() {
        assertThatThrownBy(() -> new TwilioCodeSender(new CodeSettings("twilio", 5, 500, null)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TwilioCodeSender(
                new CodeSettings("twilio", 5, 500, new CodeSettings.Twilio("AC123", "", "MG123"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theTextHasTheCodeAndNoLink() {
        assertThat(TwilioCodeSender.body("483921")).contains("483921").doesNotContain("http");
    }
}
