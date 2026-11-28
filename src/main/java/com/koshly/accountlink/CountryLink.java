package com.koshly.accountlink;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A row in country_link: an account in another country that belongs to the same
 * person as a home account (the US or Canadian account they registered with).
 * Only these IDs are shared between countries; no money data.
 */
@Entity
@Table(name = "country_link")
public class CountryLink {

    /** The added account's Firebase user ID (in that country's tenant). */
    @Id
    @Column(name = "linked_user_id", nullable = false, length = 128)
    private String linkedUserId;

    @Column(name = "linked_country", nullable = false, length = 2, updatable = false)
    private String linkedCountry;

    /** The home account's Firebase user ID: the one that pays and may add countries. */
    @Column(name = "home_user_id", nullable = false, length = 128, updatable = false)
    private String homeUserId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected CountryLink() {
        // for JPA
    }

    public CountryLink(String linkedUserId, String linkedCountry, String homeUserId) {
        this.linkedUserId = linkedUserId;
        this.linkedCountry = linkedCountry;
        this.homeUserId = homeUserId;
    }

    public String getLinkedUserId() {
        return linkedUserId;
    }

    public String getLinkedCountry() {
        return linkedCountry;
    }

    public String getHomeUserId() {
        return homeUserId;
    }
}
