package com.koshly.account;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** A card's optional promotional rate, stored in the promo_* columns of credit_card_detail. */
@Embeddable
public class CardPromo {

    @Column(name = "promo_type", length = 20)
    private String type;

    @Column(name = "promo_apr", precision = 5, scale = 2)
    private BigDecimal apr;

    @Column(name = "promo_balance", precision = 12, scale = 2)
    private BigDecimal balance;

    @Column(name = "promo_end_date")
    private LocalDate endDate;

    @Column(name = "promo_fee", precision = 12, scale = 2)
    private BigDecimal fee;

    /** Deferred interest: interest is charged back to day one if any promo balance is left at the end. */
    @Column(name = "promo_deferred_interest")
    private Boolean deferredInterest;

    protected CardPromo() {
        // for JPA
    }

    CardPromo(String type, BigDecimal apr, BigDecimal balance, LocalDate endDate, BigDecimal fee, boolean deferredInterest) {
        this.deferredInterest = deferredInterest;
        this.type = type;
        this.apr = apr;
        this.balance = balance;
        this.endDate = endDate;
        this.fee = fee;
    }

    public String getType() {
        return type;
    }

    public BigDecimal getApr() {
        return apr;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public boolean isDeferredInterest() {
        return Boolean.TRUE.equals(deferredInterest);
    }
}
