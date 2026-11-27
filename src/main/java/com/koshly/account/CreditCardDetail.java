package com.koshly.account;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Credit-card-only fields (a row in credit_card_detail), sharing its id with the account. */
@Entity
@Table(name = "credit_card_detail")
public class CreditCardDetail {

    @Id
    private UUID accountId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(name = "credit_limit", nullable = false, precision = 12, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "statement_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal statementBalance;

    @Column(name = "minimum_due", nullable = false, precision = 12, scale = 2)
    private BigDecimal minimumDue;

    @Column(name = "due_day", nullable = false)
    private int dueDay;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal apr;

    @Column(nullable = false, length = 7)
    private String color;

    /** Null when the card has no promotional rate. */
    @Embedded
    private CardPromo promo;

    protected CreditCardDetail() {
        // for JPA
    }

    CreditCardDetail(Account account) {
        this.account = account;
    }

    void apply(AccountRequest.CreditCardDetails details) {
        this.creditLimit = details.creditLimit();
        this.statementBalance = details.statementBalance();
        this.minimumDue = details.minimumDue();
        this.dueDay = details.dueDay();
        this.apr = details.apr();
        this.color = details.color();
        AccountRequest.Promo p = details.promo();
        this.promo = p == null ? null : new CardPromo(p.type(), p.apr(), p.balance(), p.endDate(), p.fee(), p.hasDeferredInterest());
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public BigDecimal getStatementBalance() {
        return statementBalance;
    }

    public BigDecimal getMinimumDue() {
        return minimumDue;
    }

    public int getDueDay() {
        return dueDay;
    }

    public BigDecimal getApr() {
        return apr;
    }

    public String getColor() {
        return color;
    }

    public CardPromo getPromo() {
        return promo;
    }
}
