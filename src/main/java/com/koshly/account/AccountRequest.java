package com.koshly.account;

import static com.koshly.account.Account.CREDIT;
import static com.koshly.account.Account.DEPOSITORY;
import static com.koshly.account.Account.LOAN;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import com.koshly.region.Market;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * JSON body for adding or updating a credit card, bank account or loan.
 *
 *   bank account: { "type": "depository", "subtype": "checking", ...shared fields }
 *   credit card:  { "type": "credit", "subtype": "credit_card", ..., "creditCard": { ... } }
 *   loan:         { "type": "loan", "subtype": "auto", ..., "loan": { ... } }
 *
 * Same rules as the app's forms, checked again here because the app can be bypassed.
 */
public record AccountRequest(

        @NotNull(message = "Type is required.")
        @Pattern(regexp = "^(depository|credit|loan)$", message = "Type must be depository, credit or loan.")
        String type,

        @NotNull(message = "Choose what kind of account this is.")
        @Pattern(regexp = "^(checking|savings|credit_card|auto|student|mortgage|personal)$",
                message = "Choose what kind of account this is.")
        String subtype,

        @NotBlank(message = "Name is required.")
        @Size(max = 40, message = "Name can be at most 40 characters.")
        @Pattern(regexp = NAME, message = NAME_MESSAGE)
        String name,

        @NotBlank(message = "Bank or lender is required.")
        @Size(max = 40, message = "Bank or lender can be at most 40 characters.")
        @Pattern(regexp = NAME, message = NAME_MESSAGE)
        String institution,

        /** Required for cards and bank accounts, optional for loans. */
        @Pattern(regexp = "^[0-9]{4}$", message = "Enter exactly the last 4 digits (never the full number).")
        String last4,

        @NotNull(message = "Balance is required.")
        @DecimalMin(value = "0.00", message = "Balance can't be negative.")
        @DecimalMax(value = MAX_MONEY, message = "Balance looks too large.")
        @Digits(integer = 9, fraction = 2, message = "Use at most 2 decimal places.")
        BigDecimal balance,

        /**
         * Country to add it to, e.g. "US" or "IN". Optional: defaults to the
         * user's current country. Its currency follows. Can't change later.
         */
        @Pattern(regexp = "^[A-Z]{2}$", message = "Country must be a 2-letter code like US or IN.")
        String country,

        /** Only for type "credit". */
        @Valid
        CreditCardDetails creditCard,

        /** Only for type "loan". */
        @Valid
        LoanDetails loan) {

    // Letters, numbers, spaces and & ' . , ( ) / + -   (same as the app)
    static final String NAME = "^[A-Za-z0-9][A-Za-z0-9 &'.,()/+-]*$";
    static final String NAME_MESSAGE = "Use only letters, numbers, spaces and & ' . , ( ) / + -";
    // Up to 99 crore rupees or ~1 billion dollars; the database allows 9,999,999,999.99.
    static final String MAX_MONEY = "999999999.99";

    private static final Set<String> DEPOSITORY_SUBTYPES = Set.of("checking", "savings");
    private static final Set<String> LOAN_SUBTYPES = Set.of("auto", "student", "mortgage", "personal");

    public record CreditCardDetails(
            @NotNull(message = "Credit limit is required.")
            @DecimalMin(value = "0.00", inclusive = false, message = "Credit limit must be more than 0.")
            @DecimalMax(value = MAX_MONEY, message = "Credit limit looks too large.")
            @Digits(integer = 9, fraction = 2, message = "Use at most 2 decimal places.")
            BigDecimal creditLimit,

            @NotNull(message = "Statement balance is required.")
            @DecimalMin(value = "0.00", message = "Statement balance can't be negative.")
            @DecimalMax(value = MAX_MONEY, message = "Statement balance looks too large.")
            @Digits(integer = 9, fraction = 2, message = "Use at most 2 decimal places.")
            BigDecimal statementBalance,

            @NotNull(message = "Minimum payment is required.")
            @DecimalMin(value = "0.00", message = "Minimum payment can't be negative.")
            @DecimalMax(value = MAX_MONEY, message = "Minimum payment looks too large.")
            @Digits(integer = 9, fraction = 2, message = "Use at most 2 decimal places.")
            BigDecimal minimumDue,

            @NotNull(message = "Payment due day is required.")
            @Min(value = 1, message = "Due day must be a day from 1 to 31.")
            @Max(value = 31, message = "Due day must be a day from 1 to 31.")
            Integer dueDay,

            @NotNull(message = "APR is required (use 0 if unknown).")
            @DecimalMin(value = "0.00", message = "APR can't be negative.")
            @DecimalMax(value = "100.00", message = "APR must be 100% or less.")
            @Digits(integer = 3, fraction = 2, message = "Enter APR as a percent, like 21.99.")
            BigDecimal apr,

            @NotNull(message = "Card color is required.")
            @Pattern(regexp = "^#(1E3A8A|0F766E|B7791F|7C3AED|BE123C|334155)$", message = "Pick one of the card colors.")
            String color,

            /** Optional: null when the card has no promotional rate. */
            @Valid
            Promo promo) {
    }

    /** A 0% balance transfer or other promotional rate on part of a card's balance. */
    public record Promo(
            @NotNull(message = "Promo type is required.")
            @Pattern(regexp = "^(balance_transfer|purchase)$", message = "Promo type must be balance_transfer or purchase.")
            String type,

            @NotNull(message = "Promo APR is required (often 0).")
            @DecimalMin(value = "0.00", message = "Promo APR can't be negative.")
            @DecimalMax(value = "100.00", message = "Promo APR must be 100% or less.")
            @Digits(integer = 3, fraction = 2, message = "Enter APR as a percent, like 0 or 2.99.")
            BigDecimal apr,

            @NotNull(message = "Promo balance is required.")
            @DecimalMin(value = "0.00", inclusive = false, message = "Promo balance must be more than 0.")
            @DecimalMax(value = MAX_MONEY, message = "Promo balance looks too large.")
            @Digits(integer = 9, fraction = 2, message = "Use at most 2 decimal places.")
            BigDecimal balance,

            @NotNull(message = "Promo end date is required.")
            LocalDate endDate,

            @NotNull(message = "Transfer fee is required (use 0 if none).")
            @DecimalMin(value = "0.00", message = "Transfer fee can't be negative.")
            @DecimalMax(value = MAX_MONEY, message = "Transfer fee looks too large.")
            @Digits(integer = 9, fraction = 2, message = "Use at most 2 decimal places.")
            BigDecimal fee,

            /** Optional, default false: interest is charged back to day one if the promo isn't paid off in time. */
            Boolean deferredInterest) {

        // Not named isX/getX, so JSON (Jackson) never sees it as a second "deferredInterest" property.
        public boolean hasDeferredInterest() {
            return Boolean.TRUE.equals(deferredInterest);
        }
    }

    public record LoanDetails(
            @NotNull(message = "Original loan amount is required.")
            @DecimalMin(value = "0.00", inclusive = false, message = "Original amount must be more than 0.")
            @DecimalMax(value = MAX_MONEY, message = "Original amount looks too large.")
            @Digits(integer = 9, fraction = 2, message = "Use at most 2 decimal places.")
            BigDecimal originalAmount,

            @NotNull(message = "Monthly payment is required.")
            @DecimalMin(value = "0.00", inclusive = false, message = "Monthly payment must be more than 0.")
            @DecimalMax(value = MAX_MONEY, message = "Monthly payment looks too large.")
            @Digits(integer = 9, fraction = 2, message = "Use at most 2 decimal places.")
            BigDecimal monthlyPayment,

            @NotNull(message = "Payment day is required.")
            @Min(value = 1, message = "Payment day must be a day from 1 to 31.")
            @Max(value = 31, message = "Payment day must be a day from 1 to 31.")
            Integer paymentDay,

            @NotNull(message = "APR is required (use 0 if unknown).")
            @DecimalMin(value = "0.00", message = "APR can't be negative.")
            @DecimalMax(value = "100.00", message = "APR must be 100% or less.")
            @Digits(integer = 3, fraction = 2, message = "Enter APR as a percent, like 5.9.")
            BigDecimal apr) {
    }

    // ---- Rules that compare fields. Each error is reported under the method's
    // name without "is", e.g. "minimumDueWithinStatement".

    @AssertTrue(message = "Choose a kind that matches the type (for example, checking for a bank account).")
    public boolean isSubtypeMatchingType() {
        if (type == null || subtype == null) {
            return true; // reported by @NotNull already
        }
        return switch (type) {
            case DEPOSITORY -> DEPOSITORY_SUBTYPES.contains(subtype);
            case CREDIT -> "credit_card".equals(subtype);
            case LOAN -> LOAN_SUBTYPES.contains(subtype);
            default -> true; // reported by @Pattern already
        };
    }

    @AssertTrue(message = "Credit cards need card details; other types must not include them.")
    public boolean isCreditCardDetailsMatchingType() {
        return type == null || CREDIT.equals(type) == (creditCard != null);
    }

    @AssertTrue(message = "Loans need loan details; other types must not include them.")
    public boolean isLoanDetailsMatchingType() {
        return type == null || LOAN.equals(type) == (loan != null);
    }

    @AssertTrue(message = "Koshly isn't available in that country yet.")
    public boolean isCountrySupported() {
        return country == null || Market.COUNTRIES.contains(country);
    }

    @AssertTrue(message = "Last 4 digits are required.")
    public boolean isLast4Provided() {
        return type == null || LOAN.equals(type) || last4 != null;
    }

    @AssertTrue(message = "Minimum payment can't be more than the statement balance.")
    public boolean isMinimumDueWithinStatement() {
        return creditCard == null || creditCard.minimumDue() == null || creditCard.statementBalance() == null
                || creditCard.minimumDue().compareTo(creditCard.statementBalance()) <= 0;
    }

    @AssertTrue(message = "Promo balance can't be more than the card's current balance.")
    public boolean isPromoWithinBalance() {
        return creditCard == null || creditCard.promo() == null || creditCard.promo().balance() == null
                || balance == null || creditCard.promo().balance().compareTo(balance) <= 0;
    }

    @AssertTrue(message = "Remaining balance can't be more than the original loan amount.")
    public boolean isBalanceWithinLoanAmount() {
        return loan == null || loan.originalAmount() == null || balance == null
                || balance.compareTo(loan.originalAmount()) <= 0;
    }
}
