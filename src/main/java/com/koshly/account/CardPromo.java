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

    protected CardPromo() {
        // for JPA
    }

    CardPromo(String type, BigDecimal apr, BigDecimal balance, LocalDate endDate, BigDecimal fee) {
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
}
