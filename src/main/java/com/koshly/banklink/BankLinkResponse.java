package com.koshly.banklink;

import java.time.Instant;
import java.util.UUID;

/** A bank connection as the app sees it. Secrets and provider IDs are never included. */
public record BankLinkResponse(
        UUID id,
        String country,
        String provider,
        String status,
        String institutionName,
        Instant consentExpiresAt,
        Instant lastSyncedAt,
        Instant createdAt) {

    static BankLinkResponse from(BankLink l) {
        return new BankLinkResponse(l.getId(), l.getCountry(), l.getProvider(), l.getStatus(), l.getInstitutionName(),
                l.getConsentExpiresAt(), l.getLastSyncedAt(), l.getCreatedAt());
    }
}
