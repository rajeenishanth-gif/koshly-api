package com.koshly.payoff;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.koshly.payoff.PayoffResult.CardAmount;
import com.koshly.payoff.PayoffResult.CardPaidOff;
import com.koshly.payoff.PayoffResult.Month;
import com.koshly.payoff.PayoffResult.Payment;
import com.koshly.payoff.PayoffResult.Reason;
import com.koshly.payoff.PayoffResult.Warning;
import com.koshly.payoff.PayoffResult.WarningType;

/**
 * Works out how to pay off credit cards and loans for the least interest, month by month.
 *
 * <p>Loans have a fixed monthly payment. A loan the user keeps on its regular
 * schedule ({@code acceptsExtra == false}, e.g. a mortgage) still takes its payment
 * out of the budget every month, but gets no extra money and doesn't count toward
 * "debt-free" or the interest totals.
 *
 * <p>Each month, in this order:
 * <ol>
 * <li><b>Minimums first.</b> A missed minimum means a late fee, a penalty APR
 *     and a hit to the credit score, so they always come first.</li>
 * <li><b>Promos just in time.</b> A promo balance (0% balance transfer and the
 *     like) only gets its minimum until money must start going to it so it's
 *     paid off the month before it ends. Until then the extra goes to cards that
 *     charge interest now. Deferred-interest promos always get this; regular
 *     promos only when their rate after the promo would be the highest one left
 *     (otherwise it's cheaper to let the avalanche deal with it later).</li>
 * <li><b>The rest by strategy:</b> highest rate first (avalanche) or smallest
 *     balance first (snowball). Balances at 0% right now are skipped: paying them
 *     early saves nothing, so that money is set aside instead.</li>
 * </ol>
 *
 * <p>Payments on one card follow the US CARD Act: the minimum may go to the
 * lowest-rate part (the promo), anything above it goes to the highest-rate part,
 * except in the last two billing cycles of a deferred-interest promo, when it
 * goes to the promo.
 *
 * <p>Plain Java with no Spring, so it's quick to unit test. Not thread-hostile:
 * every call works on its own copies.
 */
public class PayoffCalculator {

    /** Stop after 50 years: minimum payments alone may never pay a card off. */
    public static final int MAX_MONTHS = 600;

    /** How many months of the schedule to return. */
    static final int SCHEDULE_MONTHS = 24;

    /** Estimated future minimum: interest plus this share of the balance (a common US formula). */
    private static final BigDecimal MINIMUM_PRINCIPAL_SHARE = new BigDecimal("0.01");

    private static final BigDecimal TWELVE_HUNDRED = new BigDecimal("1200");
    private static final MathContext RATE = MathContext.DECIMAL64;

    public PayoffResult plan(PayoffInput input) {
        Run main = simulate(input, input.strategy(), false);
        Run baseline = simulate(input, input.strategy(), true);
        Run other = simulate(input, input.strategy().other(), false);

        List<Warning> warnings = new ArrayList<>(main.warnings);
        if (!main.paidOff) {
            warnings.add(new Warning(WarningType.NOT_PAID_OFF, null, null));
        }
        YearMonth start = YearMonth.from(input.today());
        return new PayoffResult(
                input.strategy(),
                main.minimumsThisMonth,
                main.thisMonth,
                main.setAsideThisMonth,
                main.schedule,
                main.months,
                main.paidOff,
                main.paidOff ? start.plusMonths(Math.max(0, main.months - 1)) : null,
                // Interest totals only mean something for a plan that pays the cards off.
                main.paidOff ? money(main.interest) : null,
                baseline.paidOff ? money(baseline.interest) : null,
                baseline.paidOff ? baseline.months : null,
                other.paidOff ? money(other.interest) : null,
                main.payoffOrder,
                warnings);
    }

    // ---- One run of the simulation --------------------------------------------

    /** Working state for one card or loan. Amounts are kept to the cent. */
    private static final class CardState {
        final PayoffDebt card;
        final BigDecimal monthlyRate;
        final BigDecimal promoMonthlyRate;
        final boolean deferred;
        /** Months from this month until the month the promo ends (0 = ends this month). */
        final int promoEndIndex;

        BigDecimal regular;
        BigDecimal promo;
        boolean promoActive;
        /** Interest building up in the background on a deferred-interest promo. */
        BigDecimal deferredAccrued = BigDecimal.ZERO;
        BigDecimal lastInterest = BigDecimal.ZERO;
        BigDecimal minimum = BigDecimal.ZERO;
        BigDecimal paidThisMonth = BigDecimal.ZERO;
        BigDecimal minimumPaidThisMonth = BigDecimal.ZERO;
        Reason reason;

        CardState(PayoffDebt card, YearMonth start) {
            this.card = card;
            this.monthlyRate = card.apr().divide(TWELVE_HUNDRED, RATE);
            PayoffDebt.Promo p = card.promo();
            BigDecimal total = nonNegative(card.balance());
            if (p != null && p.balance().signum() > 0) {
                this.promo = money(p.balance().min(total));
                this.regular = total.subtract(this.promo);
                this.promoMonthlyRate = p.apr().divide(TWELVE_HUNDRED, RATE);
                this.deferred = p.deferredInterest();
                this.promoEndIndex = (int) start.until(YearMonth.from(p.endDate()), java.time.temporal.ChronoUnit.MONTHS);
                this.promoActive = promoEndIndex > 0;
                if (!promoActive) { // already over
                    this.regular = this.regular.add(this.promo);
                    this.promo = BigDecimal.ZERO;
                }
            } else {
                this.promo = BigDecimal.ZERO;
                this.regular = money(total);
                this.promoMonthlyRate = BigDecimal.ZERO;
                this.deferred = false;
                this.promoEndIndex = -1;
                this.promoActive = false;
            }
        }

        BigDecimal total() {
            return regular.add(promo);
        }

        /** The last month a payment can still go to the promo before it ends (the month before the end month). */
        int deadlineIndex() {
            return promoEndIndex - 1;
        }

        /** The rate extra money on this card would save right now (extra goes to the highest-rate part). */
        BigDecimal rateForExtra() {
            if (regular.signum() > 0) {
                return monthlyRate;
            }
            return promoActive ? promoMonthlyRate : monthlyRate;
        }

        /** Pays the minimum: lowest-rate part first, as issuers may. */
        BigDecimal payMinimum(BigDecimal amount) {
            BigDecimal pay = amount.min(total());
            BigDecimal toPromo = pay.min(promo);
            promo = promo.subtract(toPromo);
            regular = regular.subtract(pay.subtract(toPromo));
            paidThisMonth = paidThisMonth.add(pay);
            minimumPaidThisMonth = minimumPaidThisMonth.add(pay);
            return pay;
        }

        /**
         * Pays above the minimum: highest-rate part first, except in the last two
         * billing cycles of a deferred-interest promo, when it goes to the promo.
         */
        BigDecimal payExtra(BigDecimal amount, int monthIndex) {
            BigDecimal pay = amount.min(total());
            boolean promoFirst = promoActive && deferred && deadlineIndex() - monthIndex <= 1;
            if (!promoFirst && promoActive && promoMonthlyRate.compareTo(monthlyRate) > 0) {
                promoFirst = true; // a promo rate above the regular rate: unusual, but follow the rule
            }
            BigDecimal first = promoFirst ? pay.min(promo) : pay.min(regular);
            BigDecimal second = pay.subtract(first);
            if (promoFirst) {
                promo = promo.subtract(first);
                regular = regular.subtract(second);
            } else {
                regular = regular.subtract(first);
                promo = promo.subtract(second);
            }
            paidThisMonth = paidThisMonth.add(pay);
            return pay;
        }
    }

    /** The outcome of one simulation. */
    private static final class Run {
        BigDecimal interest = BigDecimal.ZERO;
        int months;
        boolean paidOff;
        BigDecimal minimumsThisMonth = BigDecimal.ZERO;
        List<Payment> thisMonth = List.of();
        BigDecimal setAsideThisMonth = BigDecimal.ZERO;
        final List<Month> schedule = new ArrayList<>();
        final List<CardPaidOff> payoffOrder = new ArrayList<>();
        final List<Warning> warnings = new ArrayList<>();
    }

    /**
     * @param minimumsOnly pay exactly the minimums every month (for "compared with minimums only")
     */
    private Run simulate(PayoffInput input, PayoffStrategy strategy, boolean minimumsOnly) {
        YearMonth start = YearMonth.from(input.today());
        List<CardState> cards = input.debts().stream()
                .filter(c -> c.balance() != null && c.balance().signum() > 0)
                .map(c -> new CardState(c, start))
                .toList();
        Run run = new Run();
        Map<String, Boolean> warned = new LinkedHashMap<>();
        BigDecimal setAside = BigDecimal.ZERO;

        boolean clearAtStart = countedClear(cards);
        for (int m = 0; m < MAX_MONTHS; m++) {
            // Done when every debt in the plan is paid. Month 0 always runs when there's
            // anything to pay, so loans kept on their schedule still show this month's payment.
            if (countedClear(cards) && (m > 0 || cards.isEmpty())) {
                run.paidOff = true;
                run.months = clearAtStart ? 0 : m;
                break;
            }
            for (CardState c : cards) {
                c.paidThisMonth = BigDecimal.ZERO;
                c.minimumPaidThisMonth = BigDecimal.ZERO;
                c.reason = null;
                c.minimum = minimumFor(c, m, input.minimumFloor());
            }
            BigDecimal minimums = cards.stream().map(c -> c.minimum).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal budget = m == 0 ? input.availableNow() : input.monthlyBudget();
            BigDecimal money = minimumsOnly ? minimums : nonNegative(budget).add(setAside);
            setAside = BigDecimal.ZERO;

            if (m == 0) {
                run.minimumsThisMonth = minimums;
                if (!minimumsOnly && money.compareTo(minimums) < 0) {
                    run.warnings.add(new Warning(WarningType.BELOW_MINIMUMS, null, minimums.subtract(money)));
                }
            } else if (m == 1 && !minimumsOnly && input.monthlyBudget().compareTo(minimums) < 0) {
                run.warnings.add(new Warning(WarningType.BUDGET_BELOW_MINIMUMS, null, minimums.subtract(input.monthlyBudget())));
            }

            // 1. Minimums. If money runs short, the costliest cards get theirs first.
            List<CardState> byRate = cards.stream()
                    .sorted(Comparator.comparing((CardState c) -> c.monthlyRate).reversed())
                    .toList();
            for (CardState c : byRate) {
                BigDecimal pay = c.minimum.min(money);
                money = money.subtract(c.payMinimum(pay));
                if (pay.compareTo(c.minimum) < 0) {
                    c.reason = Reason.SHORT;
                }
            }

            if (!minimumsOnly) {
                // 2. Promos just in time.
                money = payPromoDeadlines(cards, m, money, input.monthlyBudget());
                // 3. The rest by strategy.
                money = payByStrategy(cards, strategy, m, money);
                // What's left waits in savings for the promo balances still to come; beyond
                // that it isn't needed for the cards at all.
                BigDecimal promosLeft = cards.stream().map(c -> c.promo).reduce(BigDecimal.ZERO, BigDecimal::add);
                setAside = money.min(promosLeft);
            }

            for (CardState c : cards) {
                if (c.reason == null) {
                    c.reason = !c.card.acceptsExtra() ? Reason.REGULAR_ONLY
                            : c.promoActive && c.regular.signum() == 0 ? Reason.PROMO_WAIT
                            : Reason.MINIMUM;
                }
            }
            if (m == 0) {
                run.thisMonth = cards.stream()
                        .map(c -> new Payment(c.card.id(), c.paidThisMonth, c.minimumPaidThisMonth,
                                c.paidThisMonth.subtract(c.minimumPaidThisMonth), c.reason))
                        .toList();
                run.setAsideThisMonth = setAside;
            }

            // Month end: interest, then promos that end.
            BigDecimal monthInterest = BigDecimal.ZERO;
            for (CardState c : cards) {
                BigDecimal interest = money(c.regular.multiply(c.monthlyRate));
                c.regular = c.regular.add(interest);
                if (c.promoActive && c.promo.signum() > 0) {
                    BigDecimal promoInterest = money(c.promo.multiply(c.promoMonthlyRate));
                    c.promo = c.promo.add(promoInterest);
                    interest = interest.add(promoInterest);
                    if (c.deferred) {
                        c.deferredAccrued = c.deferredAccrued.add(money(c.promo.multiply(c.monthlyRate)));
                    }
                }
                if (c.promoActive && m == c.promoEndIndex - 1) {
                    interest = interest.add(expirePromo(c, run, warned, minimumsOnly));
                }
                c.lastInterest = interest;
                if (!c.card.acceptsExtra()) {
                    continue; // kept on its regular schedule: not part of the totals
                }
                monthInterest = monthInterest.add(interest);
                if (c.total().signum() == 0 && run.payoffOrder.stream().noneMatch(p -> p.cardId().equals(c.card.id()))) {
                    run.payoffOrder.add(new CardPaidOff(c.card.id(), start.plusMonths(m)));
                }
            }
            run.interest = run.interest.add(monthInterest);

            if (m < SCHEDULE_MONTHS && !minimumsOnly) {
                run.schedule.add(new Month(
                        start.plusMonths(m),
                        cards.stream()
                                .filter(c -> c.paidThisMonth.signum() > 0)
                                .map(c -> new CardAmount(c.card.id(), c.paidThisMonth))
                                .toList(),
                        setAside,
                        cards.stream().filter(c -> c.card.acceptsExtra()).map(CardState::total)
                                .reduce(BigDecimal.ZERO, BigDecimal::add),
                        monthInterest));
            }
        }
        if (!run.paidOff && countedClear(cards)) {
            run.paidOff = true;
            run.months = MAX_MONTHS;
        }
        return run;
    }

    /**
     * Sets money aside for promo balances so each one is paid off by the month
     * before it ends, as late as possible. Works through the deadlines in order:
     * by deadline k, all promos due by then must fit in this month's money plus
     * the extra expected each month until then.
     */
    private BigDecimal payPromoDeadlines(List<CardState> cards, int m, BigDecimal money, BigDecimal monthlyBudget) {
        BigDecimal highestOtherRate = cards.stream()
                .filter(c -> c.regular.signum() > 0)
                .map(c -> c.monthlyRate)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        List<CardState> due = cards.stream()
                .filter(c -> c.promoActive && c.promo.signum() > 0 && c.deadlineIndex() >= m)
                // Regular promos only when their later rate would be the costliest; deferred ones always.
                .filter(c -> c.deferred || c.monthlyRate.compareTo(highestOtherRate) >= 0)
                .sorted(Comparator.comparingInt(CardState::deadlineIndex))
                .toList();
        // Expected money for these promos in each later month: the budget minus the other cards'
        // minimums. (A promo card's own minimum pays the promo down, so it counts.) Minimums shrink
        // as balances fall, so this errs on the safe side.
        BigDecimal otherMinimums = cards.stream()
                .filter(c -> !due.contains(c))
                .map(c -> c.minimum)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal perMonth = nonNegative(monthlyBudget.subtract(otherMinimums));

        BigDecimal needNow = BigDecimal.ZERO;
        BigDecimal cumulative = BigDecimal.ZERO;
        for (CardState c : due) {
            // Paying this promo may first clear the card's regular part (CARD Act), so count all of it.
            boolean regularFirst = !(c.deferred && c.deadlineIndex() - m <= 1);
            cumulative = cumulative.add(c.promo).add(regularFirst ? c.regular : BigDecimal.ZERO);
            BigDecimal later = perMonth.multiply(BigDecimal.valueOf(c.deadlineIndex() - m));
            needNow = needNow.max(cumulative.subtract(later));
        }
        BigDecimal reserve = needNow.min(money);
        for (CardState c : due) {
            if (reserve.signum() <= 0) {
                break;
            }
            BigDecimal before = c.promo;
            BigDecimal paid = c.payExtra(reserve.min(c.total()), m);
            reserve = reserve.subtract(paid);
            money = money.subtract(paid);
            if (c.promo.compareTo(before) < 0 || paid.signum() > 0) {
                c.reason = Reason.PROMO_DEADLINE;
            }
        }
        return money;
    }

    private BigDecimal payByStrategy(List<CardState> cards, PayoffStrategy strategy, int m, BigDecimal money) {
        Comparator<CardState> order = strategy == PayoffStrategy.AVALANCHE
                ? Comparator.comparing(CardState::rateForExtra).reversed().thenComparing(CardState::total)
                : Comparator.comparing(CardState::total).thenComparing(Comparator.comparing(CardState::rateForExtra).reversed());
        List<CardState> targets = cards.stream()
                .filter(c -> c.total().signum() > 0 && c.card.acceptsExtra())
                // Nothing saved by paying a 0% balance early: keep that money in savings.
                .filter(c -> c.rateForExtra().signum() > 0)
                .sorted(order)
                .toList();
        for (CardState c : targets) {
            if (money.signum() <= 0) {
                break;
            }
            BigDecimal paid = c.payExtra(money, m);
            money = money.subtract(paid);
            if (paid.signum() > 0 && c.reason != Reason.PROMO_DEADLINE) {
                c.reason = strategy == PayoffStrategy.AVALANCHE ? Reason.HIGHEST_RATE : Reason.SMALLEST_BALANCE;
            }
        }
        return money;
    }

    /** Ends a promo at month end. Returns interest charged back on a deferred-interest promo (else zero). */
    private BigDecimal expirePromo(CardState c, Run run, Map<String, Boolean> warned, boolean minimumsOnly) {
        BigDecimal chargedBack = BigDecimal.ZERO;
        if (c.promo.signum() > 0) {
            chargedBack = c.deferred ? c.deferredAccrued : BigDecimal.ZERO;
            if (!minimumsOnly && warned.putIfAbsent(c.card.id(), true) == null) {
                run.warnings.add(c.deferred
                        ? new Warning(WarningType.DEFERRED_INTEREST_AT_RISK, c.card.id(), c.deferredAccrued)
                        : new Warning(WarningType.PROMO_NOT_CLEARED, c.card.id(), c.promo));
            }
            c.regular = c.regular.add(c.promo).add(chargedBack);
            c.promo = BigDecimal.ZERO;
        }
        // Paid in time: the background interest is forgiven.
        c.deferredAccrued = BigDecimal.ZERO;
        c.promoActive = false;
        return chargedBack;
    }

    /** True when every debt that counts toward "debt-free" is paid off. */
    private static boolean countedClear(List<CardState> cards) {
        return cards.stream().filter(c -> c.card.acceptsExtra()).allMatch(c -> c.total().signum() == 0);
    }

    /**
     * A loan: its fixed payment. A card this month: the statement's minimum. A card
     * in later months: interest plus 1% of the balance, at least the floor.
     */
    private static BigDecimal minimumFor(CardState c, int month, BigDecimal floor) {
        BigDecimal total = c.total();
        if (total.signum() == 0) {
            return BigDecimal.ZERO;
        }
        if (month == 0 || c.card.kind() == PayoffDebt.Kind.LOAN) {
            return nonNegative(c.card.minimumDue()).min(total);
        }
        BigDecimal estimate = money(c.lastInterest.add(total.multiply(MINIMUM_PRINCIPAL_SHARE)))
                .max(nonNegative(floor));
        return estimate.min(total);
    }

    static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value == null || value.signum() < 0 ? BigDecimal.ZERO.setScale(2) : money(value);
    }
}
