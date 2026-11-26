package com.koshly.account;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JSON returned for a credit card, bank account or loan. "creditCard" is set
 * only for type credit, "loan" only for type loan. The owner's user ID is
 * never sent back.
 */
public record AccountResponse(
        UUID id,
        String type,
        String subtype,
        String name,
        String institution,
        String last4,
        BigDecimal balance,
        String source,
        CreditCardInfo creditCard,
        LoanInfo loan) {

    public record CreditCardInfo(
            BigDecimal creditLimit,
            BigDecimal statementBalance,
            BigDecimal minimumDue,
            int dueDay,
            LocalDate nextDueDate,
            BigDecimal apr,
            String color,
            AccountRequest.Promo promo) {
    }

    public record LoanInfo(
            BigDecimal originalAmount,
            BigDecimal monthlyPayment,
            int paymentDay,
            LocalDate nextPaymentDate,
            BigDecimal apr) {
    }

    static AccountResponse from(Account account, LocalDate today) {
        CreditCardDetail c = account.getCreditCard();
        LoanDetail l = account.getLoan();
        return new AccountResponse(
                account.getId(),
                account.getType(),
                account.getSubtype(),
                account.getName(),
                account.getInstitution(),
                account.getLast4(),
                account.getBalance(),
                account.getSource(),
                c == null ? null : new CreditCardInfo(
                        c.getCreditLimit(),
                        c.getStatementBalance(),
                        c.getMinimumDue(),
                        c.getDueDay(),
                        DueDates.next(c.getDueDay(), today),
                        c.getApr(),
                        c.getColor(),
                        promoOf(c.getPromo())),
                l == null ? null : new LoanInfo(
                        l.getOriginalAmount(),
                        l.getMonthlyPayment(),
                        l.getPaymentDay(),
                        DueDates.next(l.getPaymentDay(), today),
                        l.getApr()));
    }

    private static AccountRequest.Promo promoOf(CardPromo p) {
        return p == null || p.getType() == null
                ? null
                : new AccountRequest.Promo(p.getType(), p.getApr(), p.getBalance(), p.getEndDate(), p.getFee());
    }
}
