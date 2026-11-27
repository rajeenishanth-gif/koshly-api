package com.koshly.payoff;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One credit card or loan as the payoff calculator sees it.
 *
 * @param balance      total balance, including any promo balance
 * @param apr          the regular APR, as a percent (24.99)
 * @param minimumDue   a card's minimum this month, or a loan's fixed monthly payment
 * @param promo        a card's promotional balance, or null (always null for loans)
 * @param acceptsExtra whether the plan may put extra money here. Off for loans the
 *                     user keeps on their regular schedule (a mortgage, a student
 *                     loan on a forgiveness plan): their payments still come out of
 *                     the budget, but they don't count toward "debt-free".
 */
public record PayoffDebt(
        String id,
        String name,
        Kind kind,
        BigDecimal balance,
        BigDecimal apr,
        BigDecimal minimumDue,
        Promo promo,
        boolean acceptsExtra) {

    public enum Kind {
        CARD,
        /** Fixed monthly payment that doesn't shrink as the balance falls. */
        LOAN
    }

    /** A credit card. */
    public static PayoffDebt card(String id, String name, BigDecimal balance, BigDecimal apr, BigDecimal minimumDue,
            Promo promo) {
        return new PayoffDebt(id, name, Kind.CARD, balance, apr, minimumDue, promo, true);
    }

    /** A loan with a fixed monthly payment. */
    public static PayoffDebt loan(String id, String name, BigDecimal balance, BigDecimal apr, BigDecimal monthlyPayment,
            boolean acceptsExtra) {
        return new PayoffDebt(id, name, Kind.LOAN, balance, apr, monthlyPayment, null, acceptsExtra);
    }

    /**
     * @param balance          the part of the card's balance on the promo rate
     * @param apr              the promo rate, as a percent (often 0)
     * @param endDate          the day the promo rate ends
     * @param deferredInterest true when leftover promo balance on the end date
     *                         is charged interest back to the start of the promo
     */
    public record Promo(BigDecimal balance, BigDecimal apr, LocalDate endDate, boolean deferredInterest) {
    }
}
