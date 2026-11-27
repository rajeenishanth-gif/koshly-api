package com.koshly.accountlink;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Settings under "koshly.codes." in application.properties: how one-time codes
 * are sent, and the limits on text messages.
 *
 * @param sender            "log" (development: prints codes in the console) or
 *                          "twilio" (real texts). Production refuses "log".
 * @param smsPerNumberPerDay most codes one phone number gets in 24 hours, from all users together
 * @param smsPerDay         most texts the whole API sends in 24 hours; a safety cap on the bill
 * @param twilio            Twilio account settings, for sender=twilio
 */
@Validated
@ConfigurationProperties(prefix = "koshly.codes")
public record CodeSettings(
        @NotNull @Pattern(regexp = "log|twilio", message = "must be log or twilio") String sender,
        @Min(1) int smsPerNumberPerDay,
        @Min(1) int smsPerDay,
        Twilio twilio) {

    /**
     * @param accountSid          starts with "AC"
     * @param authToken           secret: from Secret Manager, never committed
     * @param messagingServiceSid starts with "MG"; its sender numbers and Geo Permissions live in the Twilio console
     */
    public record Twilio(String accountSid, String authToken, String messagingServiceSid) {
    }
}
