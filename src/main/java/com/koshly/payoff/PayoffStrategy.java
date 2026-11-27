package com.koshly.payoff;

import java.util.Locale;

/** How extra money (above the minimums) is spread across cards. */
public enum PayoffStrategy {
    /** Highest interest rate first. Costs the least interest. */
    AVALANCHE,
    /** Smallest balance first. Costs a bit more, but cards reach $0 sooner. */
    SNOWBALL;

    /** "avalanche" or "snowball", any case. Null or blank means AVALANCHE. */
    public static PayoffStrategy parse(String value) {
        if (value == null || value.isBlank()) {
            return AVALANCHE;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public PayoffStrategy other() {
        return this == AVALANCHE ? SNOWBALL : AVALANCHE;
    }
}
