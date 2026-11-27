package com.koshly.accountlink;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Like the other repositories: lookups are limited to the user who asked for the code. */
public interface VerificationCodeRepository extends JpaRepository<VerificationCode, UUID> {

    Optional<VerificationCode> findByIdAndUserId(UUID id, String userId);

    /** For the rate limit: codes this user asked for since a moment. */
    long countByUserIdAndCreatedAtAfter(String userId, Instant since);

    /** For the SMS limits: codes sent to one phone number (by anyone) since a moment. */
    long countByDestinationAndCreatedAtAfter(String destination, Instant since);

    /** For the SMS limits: all codes sent by one channel ("sms") since a moment. */
    long countByChannelAndCreatedAtAfter(String channel, Instant since);
}
