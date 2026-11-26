package com.koshly.banklink;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A row in bank_link: one connection to a bank through a provider, in one country. */
@Entity
@Table(name = "bank_link")
public class BankLink {

    public static final String PENDING = "pending";
    public static final String ACTIVE = "active";
    public static final String REVOKED = "revoked";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false, length = 128)
    private String userId;

    @Column(nullable = false, updatable = false, length = 2)
    private String country;

    @Column(nullable = false, updatable = false, length = 20)
    private String provider;

    @Column(nullable = false, length = 10)
    private String status = PENDING;

    /** Plaid item_id or Account Aggregator consent handle. */
    @Column(name = "external_id", length = 200)
    private String externalId;

    /** Encrypted provider secret (Plaid access token). Never returned by the API. */
    @Column(name = "secret_ciphertext")
    private String secretCiphertext;

    @Column(name = "institution_name", length = 100)
    private String institutionName;

    @Column(name = "consent_expires_at")
    private Instant consentExpiresAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    protected BankLink() {
        // for JPA
    }

    BankLink(String userId, String country, String provider) {
        this.userId = userId;
        this.country = country;
        this.provider = provider;
    }

    void markRevoked(Instant now) {
        this.status = REVOKED;
        this.removedAt = now;
        this.secretCiphertext = null; // nothing left to protect once access is revoked
    }

    public UUID getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getCountry() {
        return country;
    }

    public String getProvider() {
        return provider;
    }

    public String getStatus() {
        return status;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getSecretCiphertext() {
        return secretCiphertext;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public Instant getConsentExpiresAt() {
        return consentExpiresAt;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
