package com.koshly.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A row in app_user: one per person who has used the API. */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private UUID id;

    @Column(name = "firebase_uid", nullable = false, unique = true, length = 128)
    private String firebaseUid;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    /** Dashboard section order; see DashboardLayout for the format. Null means the default. */
    @Column(name = "dashboard_sections", length = 300)
    private String dashboardSections;

    /** ISO country code, e.g. "US" or "IN". */
    @Column(name = "country", nullable = false, length = 2)
    private String country;

    /** Currency for the user's totals, e.g. "USD" or "INR". */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** IANA time zone, e.g. "America/Chicago" or "Asia/Kolkata". */
    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    /** Region the user's data is stored in, e.g. "us". Changes only through a data migration. */
    @Column(name = "data_region", nullable = false, length = 20, updatable = false)
    private String dataRegion;

    protected AppUser() {
        // for JPA
    }

    public UUID getId() {
        return id;
    }

    public String getFirebaseUid() {
        return firebaseUid;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getDashboardSections() {
        return dashboardSections;
    }

    void setDashboardSections(String dashboardSections) {
        this.dashboardSections = dashboardSections;
    }

    public String getCountry() {
        return country;
    }

    public String getCurrency() {
        return currency;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public String getDataRegion() {
        return dataRegion;
    }

    void setCountry(String country) {
        this.country = country;
    }

    void setCurrency(String currency) {
        this.currency = currency;
    }

    void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }
}
