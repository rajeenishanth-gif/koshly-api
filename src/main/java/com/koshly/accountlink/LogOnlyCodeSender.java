package com.koshly.accountlink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Development stand-in: prints the code in the API's console instead of
 * sending it, so "Add a country" can be tried without an email or SMS
 * service. Never use this in production: anyone who can read the logs
 * could use the codes.
 */
@Component
class LogOnlyCodeSender implements CodeSender {

    private static final Logger log = LoggerFactory.getLogger(LogOnlyCodeSender.class);

    @Override
    public void send(Channel channel, String destination, String code) {
        log.warn("DEV ONLY - verification code for {} {}: {}", channel.value(), destination, code);
    }
}
