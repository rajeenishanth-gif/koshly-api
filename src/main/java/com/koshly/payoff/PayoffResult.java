package com.koshly.payoff;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * The plan: what to pay this month, the month-by-month schedule, and how it
 * compares with paying only the minimums.
 *
 * @param minimumsThisMonth  card minimums plus loan payments due this month
 * @param thisMonth          one line per card or loan with a balance: how much to pay and why
 * @param setAsideThisMonth  money to keep in savings for a promo deadline later
 * @param schedule           the first months of the plan
 * @param months             months until every debt in the plan is paid off (when {@code paidOff}).
 *                           Loans the user keeps on their regular schedule don't count, and
 *                           their interest isn't in the totals.
 * @param paidOff            false when the debts aren't paid off within {@link PayoffCalculator#MAX_MONTHS}
 * @param totalInterest      interest paid over the whole plan, or null when not {@code paidOff}
 * @param minimumOnlyInterest interest when paying only the minimums, or null if that never pays them off
 * @param minimumOnlyMonths  months to pay off with minimums only, or null if never
 * @param otherStrategyInterest interest with the other strategy, for comparison (null if it never pays off)
 */
public record PayoffResult(
        PayoffStrategy strategy,
        BigDecimal minimumsThisMonth,
        List<Payment> thisMonth,
        BigDecimal setAsideThisMonth,
        List<Month> schedule,
        int months,
        boolean paidOff,
        YearMonth debtFreeMonth,
        BigDecimal totalInterest,
        BigDecimal minimumOnlyInterest,
        Integer minimumOnlyMonths,
        BigDecimal otherStrategyInterest,
        List<CardPaidOff> payoffOrder,
        List<Warning> warnings) {

    /** Why a card gets what it gets this month. */
    public enum Reason {
        /** Only the minimum (or a loan's regular payment): other debts cost more right now. */
        MINIMUM,
        /** A loan's regular payment; the user keeps it off extra payments. */
        REGULAR_ONLY,
        /** Extra money: highest interest rate. */
        HIGHEST_RATE,
        /** Extra money: smallest balance (snowball). */
        SMALLEST_BALANCE,
        /** Paying the promo balance down before its end date. */
        PROMO_DEADLINE,
        /** Minimum only: the promo rate is low until its end date. */
        PROMO_WAIT,
        /** Not enough money for this card's minimum. */
        SHORT
    }

    /**
     * @param minimum the part that covers the minimum due
     * @param extra   anything above the minimum
     */
    public record Payment(String cardId, BigDecimal amount, BigDecimal minimum, BigDecimal extra, Reason reason) {
    }

    public record Month(YearMonth month, List<CardAmount> payments, BigDecimal setAside, BigDecimal remainingDebt,
            BigDecimal interest) {
    }

    public record CardAmount(String cardId, BigDecimal amount) {
    }

    public record CardPaidOff(String cardId, YearMonth month) {
    }

    public enum WarningType {
        /** The money available now doesn't cover this month's minimums. */
        BELOW_MINIMUMS,
        /** The monthly budget doesn't cover the minimums. */
        BUDGET_BELOW_MINIMUMS,
        /** At this budget a deferred-interest promo won't be paid off in time. */
        DEFERRED_INTEREST_AT_RISK,
        /** At this budget a promo balance will move to the regular rate. */
        PROMO_NOT_CLEARED,
        /** At this budget the cards aren't paid off within MAX_MONTHS. */
        NOT_PAID_OFF
    }

    /**
     * @param cardId the card it's about, or null
     * @param amount an amount that helps explain it (the shortfall, or the interest charged back), or null
     */
    public record Warning(WarningType type, String cardId, BigDecimal amount) {
    }
}
