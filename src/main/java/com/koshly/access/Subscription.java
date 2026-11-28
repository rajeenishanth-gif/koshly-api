package com.koshly.access;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A row in subscription: the plan on a home account (see V13__subscription.sql). */
@Entity
@Table(name = "subscription")
public class Subscription {

    @Id
    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "plan", nullable = false, length = 30)
    private String plan;

    @Column(name = "max_countries", nullable = false)
    private int maxCountries;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected Subscription() {
        // for JPA
    }

    public Subscription(String userId, String plan, int maxCountries, String source, String status, Instant expiresAt) {
        this.userId = userId;
        this.plan = plan;
        this.maxCountries = maxCountries;
        this.source = source;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    /** Paid up (or a failed payment the store is still retrying), and not past its end date. */
    public boolean isActiveAt(Instant now) {
        return ("active".equals(status) || "grace".equals(status)) && (expiresAt == null || expiresAt.isAfter(now));
    }

    public String getUserId() {
        return userId;
    }

    public String getPlan() {
        return plan;
    }

    public int getMaxCountries() {
        return maxCountries;
    }

    public String getSource() {
        return source;
    }

    public String getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
