package com.koshly.account;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Loan-only fields (a row in loan_detail), sharing its id with the account. */
@Entity
@Table(name = "loan_detail")
public class LoanDetail {

    @Id
    private UUID accountId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(name = "original_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "monthly_payment", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyPayment;

    @Column(name = "payment_day", nullable = false)
    private int paymentDay;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal apr;

    protected LoanDetail() {
        // for JPA
    }

    LoanDetail(Account account) {
        this.account = account;
    }

    void apply(AccountRequest.LoanDetails details) {
        this.originalAmount = details.originalAmount();
        this.monthlyPayment = details.monthlyPayment();
        this.paymentDay = details.paymentDay();
        this.apr = details.apr();
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public BigDecimal getMonthlyPayment() {
        return monthlyPayment;
    }

    public int getPaymentDay() {
        return paymentDay;
    }

    public BigDecimal getApr() {
        return apr;
    }
}
