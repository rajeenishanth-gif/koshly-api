package com.koshly.region;

import java.util.Set;

/**
 * Which mobile numbers Koshly texts codes to: only numbers of the account's own
 * country. Texting any number worldwide invites "SMS pumping", where bots ask
 * for codes to premium-rate numbers abroad and the sender pays for each text.
 */
public final class MobileNumbers {

    /**
     * Area codes that share +1 with the US but are other countries (the Caribbean
     * and Bermuda). Texts there are billed as international and are a common
     * pumping target. Canada stays allowed; US territories (Puerto Rico, Guam,
     * the Virgin Islands) are the US.
     */
    static final Set<String> PLUS_ONE_OUTSIDE_US_AND_CANADA = Set.of(
            "242", "246", "264", "268", "284", "345", "441", "473", "649", "658", "664",
            "721", "758", "767", "784", "809", "829", "849", "868", "869", "876");

    private MobileNumbers() {
    }

    /** @param e164 a number already in international form, e.g. "+14695550142" */
    public static boolean allowedFor(Market market, String e164) {
        if (e164 == null || !e164.startsWith(market.dialCode())) {
            return false;
        }
        return switch (market) {
            // +1 then 10 digits; the area code can't start with 0 or 1.
            case US -> e164.matches("^\\+1[2-9][0-9]{9}$")
                    && !PLUS_ONE_OUTSIDE_US_AND_CANADA.contains(e164.substring(2, 5));
            // +91 then 10 digits starting 6-9 (Indian mobiles).
            case IN -> e164.matches("^\\+91[6-9][0-9]{9}$");
        };
    }

    /** The message when a number isn't allowed. */
    public static String notAllowedMessage(Market market) {
        return "Use a mobile number from this account's country (it starts with " + market.dialCode() + ").";
    }
}
