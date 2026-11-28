package com.koshly.region;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Which numbers get text codes. Plain unit test. */
class MobileNumbersTest {

    @Test
    void usAccountsTextUsNumbersOnly() {
        assertThat(MobileNumbers.allowedFor(Market.US, "+14695550142")).isTrue(); // Texas
        assertThat(MobileNumbers.allowedFor(Market.US, "+17875550142")).isTrue(); // Puerto Rico is the US
        assertThat(MobileNumbers.allowedFor(Market.US, "+14165550142")).isFalse(); // Toronto is Canada
        assertThat(MobileNumbers.allowedFor(Market.US, "+18765550142")).isFalse(); // Jamaica
        assertThat(MobileNumbers.allowedFor(Market.US, "+12425550142")).isFalse(); // Bahamas
        assertThat(MobileNumbers.allowedFor(Market.US, "+11235550142")).isFalse(); // area codes can't start with 1
        assertThat(MobileNumbers.allowedFor(Market.US, "+447700900123")).isFalse(); // UK
        assertThat(MobileNumbers.allowedFor(Market.US, "+919876543210")).isFalse(); // India
    }

    @Test
    void canadianAccountsTextCanadianNumbersOnly() {
        assertThat(MobileNumbers.allowedFor(Market.CA, "+14165550142")).isTrue(); // Toronto
        assertThat(MobileNumbers.allowedFor(Market.CA, "+15145550142")).isTrue(); // Montreal
        assertThat(MobileNumbers.allowedFor(Market.CA, "+16045550142")).isTrue(); // Vancouver
        assertThat(MobileNumbers.allowedFor(Market.CA, "+14695550142")).isFalse(); // Texas
        assertThat(MobileNumbers.allowedFor(Market.CA, "+18765550142")).isFalse(); // Jamaica
        assertThat(MobileNumbers.allowedFor(Market.CA, "+919876543210")).isFalse(); // India
    }

    @Test
    void indianAccountsTextIndianMobilesOnly() {
        assertThat(MobileNumbers.allowedFor(Market.IN, "+919876543210")).isTrue();
        assertThat(MobileNumbers.allowedFor(Market.IN, "+911123456789")).isFalse(); // a Delhi landline
        assertThat(MobileNumbers.allowedFor(Market.IN, "+14695550142")).isFalse();
    }
}
