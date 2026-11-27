package com.koshly.payoff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.koshly.payoff.PayoffResult.Payment;
import com.koshly.payoff.PayoffResult.Reason;
import com.koshly.payoff.PayoffResult.WarningType;

/** The payoff rules, one at a time. Plain unit tests: no Spring, no database. */
class PayoffCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 27);
    private static final BigDecimal FLOOR = new BigDecimal("25");

    private final PayoffCalculator calculator = new PayoffCalculator();

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static PayoffDebt card(String id, String balance, String apr, String minimum) {
        return PayoffDebt.card(id, id, d(balance), d(apr), d(minimum), null);
    }

    private static PayoffDebt promoCard(String id, String balance, String apr, String minimum, LocalDate end, boolean deferred) {
        return PayoffDebt.card(id, id, d(balance), d(apr), d(minimum), new PayoffDebt.Promo(d(balance), d("0"), end, deferred));
    }

    private static PayoffDebt loan(String id, String balance, String apr, String payment, boolean acceptsExtra) {
        return PayoffDebt.loan(id, id, d(balance), d(apr), d(payment), acceptsExtra);
    }

    private PayoffResult plan(List<PayoffDebt> cards, String now, String monthly, PayoffStrategy strategy) {
        return calculator.plan(new PayoffInput(cards, d(now), d(monthly), FLOOR, strategy, TODAY));
    }

    private static Payment paymentFor(PayoffResult result, String cardId) {
        return result.thisMonth().stream().filter(p -> p.cardId().equals(cardId)).findFirst().orElseThrow();
    }

    @Test
    void paysTheHighRateCardFirstAndThePromoJustInTime() {
        // $3,000 at 25%, $3,000 on a 0% transfer ending in May, $1,000 a month.
        var high = card("high", "3000", "25", "90");
        var transfer = promoCard("transfer", "3000", "25", "30", LocalDate.of(2027, 5, 14), false);

        PayoffResult result = plan(List.of(high, transfer), "1000", "1000", PayoffStrategy.AVALANCHE);

        // This month: everything above the minimums goes to the 25% card; the promo gets its minimum.
        assertThat(paymentFor(result, "high").amount()).isEqualByComparingTo("970");
        assertThat(paymentFor(result, "high").reason()).isEqualTo(Reason.HIGHEST_RATE);
        assertThat(paymentFor(result, "transfer").amount()).isEqualByComparingTo("30");
        assertThat(paymentFor(result, "transfer").reason()).isEqualTo(Reason.PROMO_WAIT);

        // The promo is still cleared the month before it ends, so it never charges interest.
        assertThat(result.payoffOrder()).first().satisfies(p -> {
            assertThat(p.cardId()).isEqualTo("transfer");
            assertThat(p.month()).isEqualTo(YearMonth.of(2027, 4));
        });
        assertThat(result.warnings()).isEmpty();
        assertThat(result.paidOff()).isTrue();
        // Splitting $500/$500 evenly would cost about $220; this costs under $100.
        assertThat(result.totalInterest()).isLessThan(d("100"));
    }

    @Test
    void keepsMoneyInSavingsInsteadOfPayingZeroPercentEarly() {
        var transfer = promoCard("transfer", "2000", "22", "25", LocalDate.of(2027, 5, 1), false);

        PayoffResult result = plan(List.of(transfer), "1000", "1000", PayoffStrategy.AVALANCHE);

        assertThat(paymentFor(result, "transfer").amount()).isEqualByComparingTo("25");
        assertThat(result.setAsideThisMonth()).isEqualByComparingTo("975");
        assertThat(result.totalInterest()).isEqualByComparingTo("0");
        assertThat(result.debtFreeMonth()).isEqualTo(YearMonth.of(2027, 4));
    }

    @Test
    void warnsWhenADeferredInterestPromoCantBePaidInTime() {
        var store = promoCard("store", "4000", "29.99", "40", LocalDate.of(2027, 3, 1), true);

        PayoffResult result = plan(List.of(store), "200", "200", PayoffStrategy.AVALANCHE);

        // Every spare dollar goes to the deadline...
        assertThat(paymentFor(result, "store").amount()).isEqualByComparingTo("200");
        assertThat(paymentFor(result, "store").reason()).isEqualTo(Reason.PROMO_DEADLINE);
        // ...but it's not enough, and the user is told how much interest would be charged back.
        assertThat(result.warnings()).anySatisfy(w -> {
            assertThat(w.type()).isEqualTo(WarningType.DEFERRED_INTEREST_AT_RISK);
            assertThat(w.cardId()).isEqualTo("store");
            assertThat(w.amount()).isPositive();
        });
    }

    @Test
    void warnsWhenTheMoneyDoesntCoverTheMinimums() {
        var big = card("big", "5000", "20", "150");
        var small = card("small", "800", "15", "30");

        PayoffResult result = plan(List.of(big, small), "100", "500", PayoffStrategy.AVALANCHE);

        assertThat(result.minimumsThisMonth()).isEqualByComparingTo("180");
        assertThat(result.warnings()).anySatisfy(w -> {
            assertThat(w.type()).isEqualTo(WarningType.BELOW_MINIMUMS);
            assertThat(w.amount()).isEqualByComparingTo("80");
        });
        // The costlier card's minimum is covered first.
        assertThat(paymentFor(result, "big").amount()).isEqualByComparingTo("100");
        assertThat(paymentFor(result, "small").reason()).isEqualTo(Reason.SHORT);
    }

    @Test
    void avalancheCostsLessThanSnowball() {
        var big = card("big", "5000", "20", "150");
        var small = card("small", "800", "15", "30");

        PayoffResult avalanche = plan(List.of(big, small), "500", "500", PayoffStrategy.AVALANCHE);
        PayoffResult snowball = plan(List.of(big, small), "500", "500", PayoffStrategy.SNOWBALL);

        assertThat(paymentFor(avalanche, "big").reason()).isEqualTo(Reason.HIGHEST_RATE);
        assertThat(paymentFor(snowball, "small").reason()).isEqualTo(Reason.SMALLEST_BALANCE);
        assertThat(avalanche.totalInterest()).isLessThan(snowball.totalInterest());
        // Each plan also reports the other one's cost, for the comparison line.
        assertThat(avalanche.otherStrategyInterest()).isEqualByComparingTo(snowball.totalInterest());
        // Both are far cheaper than minimums only.
        assertThat(avalanche.minimumOnlyInterest()).isGreaterThan(snowball.totalInterest());
    }

    @Test
    void nothingToPayWithoutCards() {
        PayoffResult result = plan(List.of(), "500", "500", PayoffStrategy.AVALANCHE);

        assertThat(result.paidOff()).isTrue();
        assertThat(result.months()).isZero();
        assertThat(result.thisMonth()).isEmpty();
        assertThat(result.totalInterest()).isEqualByComparingTo("0");
    }

    @Test
    void saysSoWhenTheBudgetNeverPaysTheCardsOff() {
        // $25 a month never catches up with $250 a month of interest.
        var card = card("card", "10000", "29.99", "25");

        PayoffResult result = plan(List.of(card), "25", "25", PayoffStrategy.AVALANCHE);

        assertThat(result.paidOff()).isFalse();
        assertThat(result.totalInterest()).isNull();
        assertThat(result.debtFreeMonth()).isNull();
        assertThat(result.warnings()).extracting(PayoffResult.Warning::type)
                .contains(WarningType.BUDGET_BELOW_MINIMUMS, WarningType.NOT_PAID_OFF);
    }

    @Test
    void loanPaymentsComeOutOfTheMoneyFirst() {
        var card = card("card", "3000", "25", "90");
        var auto = loan("auto", "12000", "6.9", "350", true);
        var home = loan("home", "250000", "6.5", "1600", false);

        PayoffResult result = plan(List.of(card, auto, home), "3000", "2600", PayoffStrategy.AVALANCHE);

        // Both loan payments are covered, and what's left goes to the 25% card.
        assertThat(result.minimumsThisMonth()).isEqualByComparingTo("2040");
        assertThat(paymentFor(result, "home").amount()).isEqualByComparingTo("1600");
        assertThat(paymentFor(result, "home").reason()).isEqualTo(Reason.REGULAR_ONLY);
        assertThat(paymentFor(result, "auto").amount()).isEqualByComparingTo("350");
        assertThat(paymentFor(result, "card").amount()).isEqualByComparingTo("1050");
        assertThat(paymentFor(result, "card").reason()).isEqualTo(Reason.HIGHEST_RATE);

        // Card first (higher rate), then the auto loan gets the extra. The mortgage stays on its
        // schedule, so it doesn't decide the debt-free date.
        assertThat(result.payoffOrder()).extracting(PayoffResult.CardPaidOff::cardId).containsExactly("card", "auto");
        assertThat(result.debtFreeMonth()).isEqualTo(YearMonth.of(2028, 2));
    }

    @Test
    void aLoanWithAHigherRateThanACardGetsTheExtraFirst() {
        var card = card("card", "2000", "7", "40");
        var personal = loan("personal", "5000", "14.99", "200", true);

        PayoffResult result = plan(List.of(card, personal), "1000", "1000", PayoffStrategy.AVALANCHE);

        assertThat(paymentFor(result, "personal").reason()).isEqualTo(Reason.HIGHEST_RATE);
        assertThat(paymentFor(result, "card").reason()).isEqualTo(Reason.MINIMUM);
    }

    @Test
    void aLoanKeptOnScheduleStillShowsItsPayment() {
        var home = loan("home", "250000", "6.5", "1600", false);

        PayoffResult result = plan(List.of(home), "2000", "2000", PayoffStrategy.AVALANCHE);

        assertThat(paymentFor(result, "home").amount()).isEqualByComparingTo("1600");
        assertThat(paymentFor(result, "home").reason()).isEqualTo(Reason.REGULAR_ONLY);
        // Nothing in the plan to pay off early.
        assertThat(result.months()).isZero();
        assertThat(result.totalInterest()).isEqualByComparingTo("0");
    }

    @Test
    void parsesTheStrategy() {
        assertThat(PayoffStrategy.parse(null)).isEqualTo(PayoffStrategy.AVALANCHE);
        assertThat(PayoffStrategy.parse("Snowball")).isEqualTo(PayoffStrategy.SNOWBALL);
        assertThatThrownBy(() -> PayoffStrategy.parse("fastest")).isInstanceOf(IllegalArgumentException.class);
    }
}
