package com.koshly.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.koshly.account.AccountResponse;
import com.koshly.account.AccountService;
import com.koshly.region.AccountCountry;
import com.koshly.region.Market;
import com.koshly.user.UserService;

/**
 * The dashboard's totals and alerts for the signed-in account's country. Builds on the same
 * account list the app shows (AccountService.list), so ownership, the
 * country filter and removed accounts are handled in one place.
 */
@Service
public class DashboardService {

    /** Payments due within this many days count as "upcoming". */
    static final int UPCOMING_DAYS = 30;

    /** Average month length, for "months left" on a promo. */
    private static final BigDecimal DAYS_PER_MONTH = new BigDecimal("30.44");

    private final AccountService accounts;
    private final UserService users;
    private final AccountCountry accountCountry;

    public DashboardService(AccountService accounts, UserService users, AccountCountry accountCountry) {
        this.accounts = accounts;
        this.users = users;
        this.accountCountry = accountCountry;
    }

    @Transactional(readOnly = true)
    public DashboardResponse summary(Jwt jwt) {
        List<AccountResponse> all = accounts.list(jwt);
        Market market = accountCountry.of(jwt);
        LocalDate today = users.today(jwt);

        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal cardDebt = BigDecimal.ZERO;
        BigDecimal loanDebt = BigDecimal.ZERO;
        BigDecimal totalLimit = BigDecimal.ZERO;
        List<DashboardResponse.UpcomingPayment> upcoming = new ArrayList<>();
        List<DashboardResponse.PromoAlert> promos = new ArrayList<>();

        for (AccountResponse a : all) {
            switch (a.type()) {
                case "depository" -> cash = cash.add(a.balance());
                case "credit" -> {
                    var c = a.creditCard();
                    cardDebt = cardDebt.add(a.balance());
                    totalLimit = totalLimit.add(c.creditLimit());
                    String detail = a.institution() + " ···· " + a.last4();
                    if (c.minimumDue().signum() > 0) {
                        upcoming.add(new DashboardResponse.UpcomingPayment(a.id(), "card", a.name(), detail,
                                c.minimumDue(), c.statementBalance(), c.nextDueDate(), daysBetween(today, c.nextDueDate())));
                    }
                    var promo = c.promo();
                    if (promo != null && promo.balance().signum() > 0) {
                        long daysLeft = daysBetween(today, promo.endDate());
                        int monthsLeft = Math.max(1, BigDecimal.valueOf(daysLeft)
                                .divide(DAYS_PER_MONTH, 0, RoundingMode.CEILING).intValue());
                        BigDecimal monthly = promo.balance().divide(BigDecimal.valueOf(monthsLeft), 2, RoundingMode.CEILING);
                        promos.add(new DashboardResponse.PromoAlert(a.id(), a.name(), detail, promo, daysLeft, monthsLeft,
                                monthly));
                    }
                }
                case "loan" -> {
                    var l = a.loan();
                    loanDebt = loanDebt.add(a.balance());
                    upcoming.add(new DashboardResponse.UpcomingPayment(a.id(), "loan", a.name(), a.institution(),
                            l.monthlyPayment(), null, l.nextPaymentDate(), daysBetween(today, l.nextPaymentDate())));
                }
                default -> {
                    // unknown types are ignored rather than breaking the dashboard
                }
            }
        }

        List<DashboardResponse.UpcomingPayment> dueSoon = upcoming.stream()
                .filter(p -> p.daysLeft() <= UPCOMING_DAYS)
                .sorted(Comparator.comparingLong(DashboardResponse.UpcomingPayment::daysLeft))
                .toList();
        promos.sort(Comparator.comparingLong(DashboardResponse.PromoAlert::daysLeft));

        BigDecimal totalDebt = cardDebt.add(loanDebt);
        BigDecimal utilization = totalLimit.signum() > 0
                ? cardDebt.divide(totalLimit, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new DashboardResponse(
                market.country(),
                market.currency(),
                today,
                cash.subtract(totalDebt),
                cash,
                cardDebt,
                loanDebt,
                totalDebt,
                totalLimit,
                totalLimit.subtract(cardDebt).max(BigDecimal.ZERO),
                utilization,
                dueSoon,
                dueSoon.stream().map(DashboardResponse.UpcomingPayment::amount).reduce(BigDecimal.ZERO, BigDecimal::add),
                (int) dueSoon.stream().filter(p -> p.daysLeft() < 0).count(),
                promos);
    }

    private static long daysBetween(LocalDate today, LocalDate date) {
        return ChronoUnit.DAYS.between(today, date);
    }
}
