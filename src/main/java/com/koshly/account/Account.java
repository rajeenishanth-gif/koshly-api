package com.koshly.account;

import com.koshly.region.Market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One credit card, bank account or loan (a row in "account").
 *
 * Shared fields live here. A credit card also has a {@link CreditCardDetail},
 * a loan a {@link LoanDetail}; a bank account needs nothing extra. Money is
 * BigDecimal: exact, never floating point.
 */
@Entity
@Table(name = "account")
public class Account {

    public static final String DEPOSITORY = "depository";
    public static final String CREDIT = "credit";
    public static final String LOAN = "loan";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Firebase user ID of the owner. Set once, never changed. */
    @Column(name = "user_id", nullable = false, updatable = false, length = 128)
    private String userId;

    /** depository | credit | loan. Set once, never changed. */
    @Column(nullable = false, updatable = false, length = 20)
    private String type;

    /** checking | savings | credit_card | auto | student | mortgage | personal */
    @Column(nullable = false, length = 20)
    private String subtype;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(nullable = false, length = 40)
    private String institution;

    @Column(length = 4)
    private String last4;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balance;

    /** Country it belongs to, e.g. "US" or "IN". Set once; the app shows one country at a time. */
    @Column(nullable = false, length = 2, updatable = false)
    private String country = "US";

    /** ISO currency code of every amount on this account: always its country's currency. */
    @Column(nullable = false, length = 3)
    private String currency = "USD";

    /** manual (typed in by the user) or plaid (linked). */
    @Column(nullable = false, length = 10)
    private String source = "manual";

    /** Plaid's id for this account, once bank linking exists. */
    @Column(name = "plaid_account_id", length = 100)
    private String plaidAccountId;

    @OneToOne(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    private CreditCardDetail creditCard;

    @OneToOne(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    private LoanDetail loan;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** When the user removed it; null while in use. Removed accounts are deleted for good later. */
    @Column(name = "removed_at")
    private Instant removedAt;

    protected Account() {
        // for JPA
    }

    public Account(String userId, String type) {
        this.userId = userId;
        this.type = type;
    }

    /** Hides the account from the user. A clean-up job deletes it after the retention period. */
    void markRemoved(Instant now) {
        this.removedAt = now;
    }

    /** Undoes {@link #markRemoved}. */
    void restore() {
        this.removedAt = null;
    }

    /** Linked through Plaid (rather than typed in by the user). */
    public boolean isLinked() {
        return "plaid".equals(source);
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    /** Copies every editable field from a validated request (whose type matches this account). */
    /**
     * @param country for a new account: the country it's added to. Ignored for
     *                existing accounts, whose country never changes.
     */
    void apply(AccountRequest request, Market country) {
        if (id == null) {
            this.country = country.country();
            this.currency = country.currency();
        }
        this.subtype = request.subtype();
        this.name = request.name().trim();
        this.institution = request.institution().trim();
        this.last4 = request.last4();
        this.balance = request.balance();

        if (CREDIT.equals(type)) {
            if (creditCard == null) {
                creditCard = new CreditCardDetail(this);
            }
            creditCard.apply(request.creditCard());
        } else if (LOAN.equals(type)) {
            if (loan == null) {
                loan = new LoanDetail(this);
            }
            loan.apply(request.loan());
        }
    }

    public UUID getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getType() {
        return type;
    }

    public String getSubtype() {
        return subtype;
    }

    public String getName() {
        return name;
    }

    public String getInstitution() {
        return institution;
    }

    public String getLast4() {
        return last4;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCountry() {
        return country;
    }

    public String getSource() {
        return source;
    }

    public String getPlaidAccountId() {
        return plaidAccountId;
    }

    public CreditCardDetail getCreditCard() {
        return creditCard;
    }

    public LoanDetail getLoan() {
        return loan;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
