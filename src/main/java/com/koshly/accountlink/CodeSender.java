package com.koshly.accountlink;

/**
 * Delivers a one-time code by email or text message. The version in use
 * today only writes the code to the API's log, for development; replace it
 * with one that uses an email service (for example SendGrid or Amazon SES)
 * and an SMS service (for example Twilio) before launch.
 */
public interface CodeSender {

    void send(Channel channel, String destination, String code);
}
