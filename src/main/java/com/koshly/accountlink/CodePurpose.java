package com.koshly.accountlink;

/** What a one-time code is for. Stored in verification_code.purpose. */
public enum CodePurpose {
    /** "Add a country": proves an email or phone before opening that country's account. */
    ADD_COUNTRY("add_country"),
    /** Verifies the mobile number on the user's own account. */
    PHONE("phone");

    private final String value;

    CodePurpose(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static CodePurpose from(String value) {
        for (CodePurpose p : values()) {
            if (p.value.equals(value)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unknown purpose: " + value);
    }
}
