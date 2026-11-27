package com.koshly.payoff;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * JSON for GET /api/payoff-plan. Amounts are in {@code currency}. Months are
 * "2027-04" strings. An estimate from the numbers the user entered, not advice.
 *
 * @param cash              checking and savings together
 * @param availableNow      cash minus the cushion: what can go to debts this month
 * @param monthlyBudgetDefaulted true when no budget was sent and the minimums were used instead
 * @param minimumsThisMonth card minimums plus loan payments due this month
 * @param totalDebt         the balances of the debts in the plan (loans kept on their schedule aren't included)
 * @param debts             one entry per card or loan with a balance, with this month's payment
 */
public record PayoffPlanResponse(
        String currency,
        LocalDate today,
        String strategy,
        BigDecimal cash,
        BigDecimal cushion,
        BigDecimal availableNow,
        BigDecimal monthlyBudget,
        boolean monthlyBudgetDefaulted,
        BigDecimal minimumsThisMonth,
        BigDecimal setAsideThisMonth,
        BigDecimal totalDebt,
        List<Debt> debts,
        boolean paidOff,
        Integer months,
        YearMonth debtFreeMonth,
        BigDecimal totalInterest,
        BigDecimal minimumOnlyInterest,
        Integer minimumOnlyMonths,
        BigDecimal interestSaved,
        String otherStrategy,
        BigDecimal otherStrategyInterest,
        List<Month> schedule,
        List<Warning> warnings) {

    /**
     * @param kind         card | loan
     * @param subtype      credit_card, or the loan type: auto | student | mortgage | personal
     * @param color        a card's color (null for loans)
     * @param minimumDue   a card's minimum, or a loan's monthly payment
     * @param acceptsExtra false for a loan kept on its regular schedule
     * @param reason       minimum | regular_only | highest_rate | smallest_balance | promo_deadline | promo_wait | short
     * @param paidOffMonth the month the plan pays this off, or null if not within the plan
     */
    public record Debt(
            UUID id,
            String kind,
            String subtype,
            String name,
            String detail,
            String color,
            BigDecimal balance,
            BigDecimal apr,
            BigDecimal minimumDue,
            boolean acceptsExtra,
            Promo promo,
            BigDecimal payThisMonth,
            BigDecimal minimumPart,
            BigDecimal extraPart,
            String reason,
            YearMonth paidOffMonth) {
    }

    public record Promo(BigDecimal balance, BigDecimal apr, LocalDate endDate, boolean deferredInterest) {
    }

    public record Month(YearMonth month, List<Payment> payments, BigDecimal setAside, BigDecimal remainingDebt,
            BigDecimal interest) {
    }

    public record Payment(UUID debtId, BigDecimal amount) {
    }

    /**
     * @param type   below_minimums | budget_below_minimums | deferred_interest_at_risk | promo_not_cleared | not_paid_off
     * @param amount the shortfall, the interest that would be charged back, or the promo balance left (or null)
     */
    public record Warning(String type, UUID debtId, BigDecimal amount) {
    }
}
