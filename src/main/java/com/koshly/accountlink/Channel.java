package com.koshly.accountlink;

/** Where a code is sent: an email address or a mobile number (by text message). */
public enum Channel {
    EMAIL("email"),
    SMS("sms");

    private final String value;

    Channel(String value) {
        this.value = value;
    }

    /** As stored and as sent in JSON: "email" or "sms". */
    public String value() {
        return value;
    }

    public static Channel from(String value) {
        for (Channel c : values()) {
            if (c.value.equals(value)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Unknown channel: " + value);
    }
}
