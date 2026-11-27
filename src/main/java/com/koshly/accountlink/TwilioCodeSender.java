package com.koshly.accountlink;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sends codes by text message through Twilio's Messages API (plain HTTPS, no
 * SDK). Email codes aren't connected yet: those return 503 until an email
 * service is added (only "Add a country" uses them, and only India needs that).
 *
 * <p>In the Twilio console, also turn on Geo Permissions for the US only (and
 * India later) and SMS Pumping Protection on the Messaging Service.
 */
@Component
@ConditionalOnProperty(name = "koshly.codes.sender", havingValue = "twilio")
class TwilioCodeSender implements CodeSender {

    private static final Logger log = LoggerFactory.getLogger(TwilioCodeSender.class);

    private final CodeSettings.Twilio twilio;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    TwilioCodeSender(CodeSettings settings) {
        this.twilio = settings.twilio();
        if (twilio == null || blank(twilio.accountSid()) || blank(twilio.authToken()) || blank(twilio.messagingServiceSid())) {
            throw new IllegalStateException("koshly.codes.sender=twilio needs TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN "
                    + "and TWILIO_MESSAGING_SERVICE_SID.");
        }
    }

    @Override
    public void send(Channel channel, String destination, String code) {
        if (channel != Channel.SMS) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Email codes aren't available yet. Use your mobile number instead.");
        }
        String form = "To=" + enc(destination) + "&MessagingServiceSid=" + enc(twilio.messagingServiceSid())
                + "&Body=" + enc(body(code));
        String auth = Base64.getEncoder().encodeToString(
                (twilio.accountSid() + ":" + twilio.authToken()).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("https://api.twilio.com/2010-04-01/Accounts/" + twilio.accountSid() + "/Messages.json"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                // Never log the code or the full number; the status is enough to investigate.
                log.error("Twilio refused a text (HTTP {}).", response.statusCode());
                throw failed(null);
            }
        } catch (IOException e) {
            throw failed(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failed(e);
        }
    }

    /** The text people receive. No link in it, so it can't be mistaken for phishing. */
    static String body(String code) {
        return "Your Koshly code is " + code + ". It expires in 10 minutes. Koshly will never call to ask for it.";
    }

    private static ResponseStatusException failed(Exception cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "We couldn't send the text right now. Please try again in a few minutes.", cause);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
