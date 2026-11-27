package com.koshly.accountlink;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

/** A row in verification_code: one code sent to an email or phone, used once. */
@Entity
@Table(name = "verification_code")
public class VerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false, length = 128)
    private String userId;

    @Column(nullable = false, updatable = false, length = 2)
    private String country;

    @Column(nullable = false, updatable = false, length = 10)
    private String channel;

    @Column(nullable = false, updatable = false, length = 254)
    private String destination;

    /** BCrypt hash of the code; the code itself is never stored. */
    @Column(name = "code_hash", nullable = false, updatable = false, length = 100)
    private String codeHash;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VerificationCode() {
        // for JPA
    }

    VerificationCode(String userId, String country, Channel channel, String destination, String codeHash,
            Instant expiresAt) {
        this.userId = userId;
        this.country = country;
        this.channel = channel.value();
        this.destination = destination;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    void countAttempt() {
        attempts++;
    }

    void markUsed(Instant now) {
        usedAt = now;
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

    public Channel getChannel() {
        return Channel.from(channel);
    }

    public String getDestination() {
        return destination;
    }

    String getCodeHash() {
        return codeHash;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
