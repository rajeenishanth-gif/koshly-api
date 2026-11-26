package com.koshly.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.koshly.account.AccountRequest;

/**
 * Everything the dashboard's numbers need, for one country, in its currency.
 * Worked out on the server so the web app and the phone apps always agree.
 *
 * @param asOf        "today" in the user's time zone, which the due dates count from
 * @param utilization card balances divided by total credit limit, 0 to 1 (4 decimals)
 */
public record DashboardResponse(
        String country,
        String currency,
        LocalDate asOf,
        BigDecimal netBalance,
        BigDecimal cash,
        BigDecimal cardDebt,
        BigDecimal loanDebt,
        BigDecimal totalDebt,
        BigDecimal totalLimit,
        BigDecimal availableCredit,
        BigDecimal utilization,
        List<UpcomingPayment> upcoming,
        BigDecimal dueSoonTotal,
        int overdueCount,
        List<PromoAlert> promos) {

    /**
     * A card or loan payment due soon.
     *
     * @param kind       "card" or "loan"
     * @param amount     minimum due (cards) or monthly payment (loans)
     * @param fullAmount statement balance, cards only
     * @param daysLeft   0 = due today; negative = overdue
     */
    public record UpcomingPayment(
            UUID id,
            String kind,
            String name,
            String detail,
            BigDecimal amount,
            BigDecimal fullAmount,
            LocalDate dueDate,
            long daysLeft) {
    }

    /**
     * A promotional rate (e.g. a 0% balance transfer) and how to clear it in time.
     *
     * @param monthlyToClear pay this every month to clear the promo balance before it ends
     */
    public record PromoAlert(
            UUID cardId,
            String cardName,
            String detail,
            AccountRequest.Promo promo,
            long daysLeft,
            int monthsLeft,
            BigDecimal monthlyToClear) {
    }
}
