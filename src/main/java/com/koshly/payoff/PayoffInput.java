package com.koshly.payoff;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the calculator needs.
 *
 * @param debts         cards and loans
 * @param availableNow  money that can go to debts this month (cash minus the cushion the user keeps)
 * @param monthlyBudget money that can go to debts each month after this one, loan payments included
 * @param minimumFloor  the smallest minimum payment a card asks for (e.g. $25 in the US),
 *                      used to estimate future minimums
 * @param today         the user's today, in their time zone
 */
public record PayoffInput(
        List<PayoffDebt> debts,
        BigDecimal availableNow,
        BigDecimal monthlyBudget,
        BigDecimal minimumFloor,
        PayoffStrategy strategy,
        LocalDate today) {
}
