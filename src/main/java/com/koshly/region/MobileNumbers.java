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
     * pumping target. US territories (Puerto Rico, Guam, the Virgin Islands) are
     * the US. Canada is its own country (see CANADA).
     */
    static final Set<String> PLUS_ONE_OUTSIDE_US_AND_CANADA = Set.of(
            "242", "246", "264", "268", "284", "345", "441", "473", "649", "658", "664",
            "721", "758", "767", "784", "809", "829", "849", "868", "869", "876");

    /**
     * Canada's area codes (all share +1 with the US), including ones already
     * announced for the next few years (273 Quebec 2027, 568 Alberta 2028, 851
     * Nova Scotia/PEI 2028), so new numbers work the day they're issued. Check the
     * Canadian Numbering Administrator (cnac.ca) when the CRTC adds another.
     */
    static final Set<String> CANADA = Set.of(
            // British Columbia
            "236", "250", "257", "604", "672", "778",
            // Alberta
            "368", "403", "568", "587", "780", "825",
            // Saskatchewan, Manitoba
            "306", "474", "639", "204", "431", "584",
            // Ontario
            "226", "249", "289", "343", "365", "382", "416", "437", "519", "548", "613", "647", "683",
            "705", "742", "753", "807", "905", "942",
            // Quebec
            "263", "273", "354", "367", "418", "438", "450", "468", "514", "579", "581", "819", "873",
            // Atlantic provinces
            "428", "506", "782", "851", "902", "709", "879",
            // Yukon, Northwest Territories, Nunavut
            "867");

    private MobileNumbers() {
    }

    /** @param e164 a number already in international form, e.g. "+14695550142" */
    public static boolean allowedFor(Market market, String e164) {
        if (e164 == null || !e164.startsWith(market.dialCode())) {
            return false;
        }
        return switch (market) {
            // +1 then 10 digits; the area code can't start with 0 or 1. Not Canada or the Caribbean.
            case US -> e164.matches("^\\+1[2-9][0-9]{9}$")
                    && !PLUS_ONE_OUTSIDE_US_AND_CANADA.contains(e164.substring(2, 5))
                    && !CANADA.contains(e164.substring(2, 5));
            // +1 then 10 digits with a Canadian area code.
            case CA -> e164.matches("^\\+1[2-9][0-9]{9}$") && CANADA.contains(e164.substring(2, 5));
            // +91 then 10 digits starting 6-9 (Indian mobiles).
            case IN -> e164.matches("^\\+91[6-9][0-9]{9}$");
        };
    }

    /** The message when a number isn't allowed. */
    public static String notAllowedMessage(Market market) {
        return "Use a mobile number from this account's country (it starts with " + market.dialCode() + ").";
    }
}
