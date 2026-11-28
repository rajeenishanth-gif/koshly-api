package com.koshly.payoff;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.koshly.account.AccountResponse;
import com.koshly.account.AccountService;
import com.koshly.region.AccountCountry;
import com.koshly.region.Market;
import com.koshly.user.UserService;

/**
 * Builds the payoff plan for the signed-in account: reads its cards, loans and
 * cash (the same list the app shows) and hands them to {@link PayoffCalculator}.
 */
@Service
public class PayoffService {

    /** Largest budget or cushion accepted, same ceiling as account amounts. */
    static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999.99");

    /** The smallest minimum payment cards usually ask for, per country (to estimate future minimums). */
    private static final Map<Market, BigDecimal> MINIMUM_FLOOR = Map.of(
            Market.US, new BigDecimal("25"),
            Market.CA, new BigDecimal("10"),
            Market.IN, new BigDecimal("200"));

    /**
     * Loans that get extra payments unless the user says otherwise. Mortgages are
     * usually cheap money, and extra payments on a student loan can be wasted on a
     * forgiveness plan, so those stay on their regular schedule by default.
     */
    private static final Set<String> EXTRA_BY_DEFAULT = Set.of("auto", "personal");

    private final AccountService accounts;
    private final UserService users;
    private final AccountCountry accountCountry;
    private final PayoffCalculator calculator = new PayoffCalculator();

    public PayoffService(AccountService accounts, UserService users, AccountCountry accountCountry) {
        this.accounts = accounts;
        this.users = users;
        this.accountCountry = accountCountry;
    }

    /**
     * @param monthlyBudget money for debts each month after this one, loan payments included;
     *                      null = just the minimums and loan payments
     * @param cushion       cash to keep in the bank, never planned for debts; null = 0
     * @param strategy      "avalanche" (default) or "snowball"
     * @param extraLoans    comma-separated ids of the loans that may get extra payments;
     *                      null = the defaults (auto and personal loans), "" = none
     */
    @Transactional(readOnly = true)
    public PayoffPlanResponse plan(Jwt jwt, BigDecimal monthlyBudget, BigDecimal cushion, String strategy,
            String extraLoans) {
        PayoffStrategy chosen = parseStrategy(strategy);
        checkAmount(monthlyBudget, "Monthly amount");
        checkAmount(cushion, "Cushion");
        Set<UUID> chosenLoans = parseIds(extraLoans);

        Market market = accountCountry.of(jwt);
        LocalDate today = users.today(jwt);
        List<AccountResponse> all = accounts.list(jwt);

        BigDecimal cash = all.stream()
                .filter(a -> "depository".equals(a.type()))
                .map(AccountResponse::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<AccountResponse> debtAccounts = all.stream()
                .filter(a -> a.balance().signum() > 0)
                .filter(a -> ("credit".equals(a.type()) && a.creditCard() != null)
                        || ("loan".equals(a.type()) && a.loan() != null))
                .toList();
        List<PayoffDebt> debts = debtAccounts.stream()
                .map(a -> toDebt(a, chosenLoans))
                .toList();

        BigDecimal keep = cushion == null ? BigDecimal.ZERO : cushion;
        BigDecimal availableNow = cash.subtract(keep).max(BigDecimal.ZERO);
        BigDecimal minimums = debts.stream().map(d -> d.minimumDue().min(d.balance()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean defaulted = monthlyBudget == null;
        BigDecimal budget = defaulted ? minimums : monthlyBudget;

        PayoffResult r = calculator.plan(new PayoffInput(
                debts, availableNow, budget, MINIMUM_FLOOR.getOrDefault(market, BigDecimal.ZERO), chosen, today));

        Map<String, PayoffResult.Payment> payments = r.thisMonth().stream()
                .collect(Collectors.toMap(PayoffResult.Payment::cardId, Function.identity()));
        Map<String, PayoffResult.CardPaidOff> paidOff = r.payoffOrder().stream()
                .collect(Collectors.toMap(PayoffResult.CardPaidOff::cardId, Function.identity()));
        Map<String, PayoffDebt> byId = debts.stream().collect(Collectors.toMap(PayoffDebt::id, Function.identity()));

        List<PayoffPlanResponse.Debt> lines = debtAccounts.stream()
                .map(a -> {
                    String id = a.id().toString();
                    PayoffDebt debt = byId.get(id);
                    var pay = payments.get(id);
                    var off = paidOff.get(id);
                    var c = a.creditCard();
                    var p = c == null ? null : c.promo();
                    boolean isCard = debt.kind() == PayoffDebt.Kind.CARD;
                    return new PayoffPlanResponse.Debt(
                            a.id(),
                            isCard ? "card" : "loan",
                            a.subtype(),
                            a.name(),
                            isCard ? a.institution() + " ···· " + a.last4() : a.institution(),
                            isCard ? c.color() : null,
                            a.balance(),
                            debt.apr(),
                            debt.minimumDue(),
                            debt.acceptsExtra(),
                            p == null ? null
                                    : new PayoffPlanResponse.Promo(p.balance(), p.apr(), p.endDate(), p.hasDeferredInterest()),
                            pay == null ? BigDecimal.ZERO : pay.amount(),
                            pay == null ? BigDecimal.ZERO : pay.minimum(),
                            pay == null ? BigDecimal.ZERO : pay.extra(),
                            pay == null ? null : code(pay.reason()),
                            off == null ? null : off.month());
                })
                .toList();

        BigDecimal saved = r.totalInterest() != null && r.minimumOnlyInterest() != null
                ? r.minimumOnlyInterest().subtract(r.totalInterest()).max(BigDecimal.ZERO)
                : null;

        return new PayoffPlanResponse(
                market.currency(),
                today,
                code(chosen),
                cash,
                keep,
                availableNow,
                budget,
                defaulted,
                r.minimumsThisMonth(),
                r.setAsideThisMonth(),
                debts.stream().filter(PayoffDebt::acceptsExtra).map(PayoffDebt::balance)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                lines,
                r.paidOff(),
                r.paidOff() ? r.months() : null,
                r.debtFreeMonth(),
                r.totalInterest(),
                r.minimumOnlyInterest(),
                r.minimumOnlyMonths(),
                saved,
                code(chosen.other()),
                r.otherStrategyInterest(),
                r.schedule().stream()
                        .map(m -> new PayoffPlanResponse.Month(m.month(),
                                m.payments().stream()
                                        .map(p -> new PayoffPlanResponse.Payment(UUID.fromString(p.cardId()), p.amount()))
                                        .toList(),
                                m.setAside(), m.remainingDebt(), m.interest()))
                        .toList(),
                r.warnings().stream()
                        .map(w -> new PayoffPlanResponse.Warning(code(w.type()),
                                w.cardId() == null ? null : UUID.fromString(w.cardId()), w.amount()))
                        .toList());
    }

    private static PayoffDebt toDebt(AccountResponse a, Set<UUID> chosenLoans) {
        if (a.creditCard() != null) {
            var c = a.creditCard();
            var p = c.promo();
            return PayoffDebt.card(a.id().toString(), a.name(), a.balance(), c.apr(), c.minimumDue(),
                    p == null ? null : new PayoffDebt.Promo(p.balance(), p.apr(), p.endDate(), p.hasDeferredInterest()));
        }
        var l = a.loan();
        boolean extra = chosenLoans == null ? EXTRA_BY_DEFAULT.contains(a.subtype()) : chosenLoans.contains(a.id());
        return PayoffDebt.loan(a.id().toString(), a.name(), a.balance(), l.apr(), l.monthlyPayment(), extra);
    }

    /** "id1,id2" -> ids; null -> null (use the defaults); "" -> no loans. */
    private static Set<UUID> parseIds(String value) {
        if (value == null) {
            return null;
        }
        Set<UUID> ids = new HashSet<>();
        for (String part : value.split(",")) {
            if (part.isBlank()) {
                continue;
            }
            try {
                ids.add(UUID.fromString(part.trim()));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "extraLoans must be loan ids separated by commas.");
            }
        }
        return ids;
    }

    private static PayoffStrategy parseStrategy(String value) {
        try {
            return PayoffStrategy.parse(value);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Strategy must be avalanche or snowball.");
        }
    }

    private static void checkAmount(BigDecimal value, String label) {
        if (value != null && (value.signum() < 0 || value.compareTo(MAX_AMOUNT) > 0 || value.scale() > 2)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    label + " must be between 0 and " + MAX_AMOUNT.toPlainString() + ", with at most 2 decimal places.");
        }
    }

    /** HIGHEST_RATE -> "highest_rate" */
    private static String code(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
