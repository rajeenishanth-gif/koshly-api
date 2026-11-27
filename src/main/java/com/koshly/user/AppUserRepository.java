package com.koshly.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /**
     * Deletes the user's row; the database deletes their accounts, bank links
     * and codes with it (ON DELETE CASCADE). Flushes pending changes first and
     * clears the session after, so nothing stale is written back.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM app_user WHERE firebase_uid = :firebaseUid", nativeQuery = true)
    int deleteByFirebaseUid(@Param("firebaseUid") String firebaseUid);

    Optional<AppUser> findByFirebaseUid(String firebaseUid);

    /**
     * Creates the user's row the first time they save something. If it already
     * exists, does nothing. Safe even if two requests arrive at the same moment.
     */
    @Modifying
    @Query(value = """
            INSERT INTO app_user (firebase_uid, email, data_region, country, currency, time_zone)
            VALUES (:firebaseUid, :email, :dataRegion, :country, :currency, :timeZone)
            ON CONFLICT (firebase_uid) DO NOTHING
            """, nativeQuery = true)
    void insertIfMissing(@Param("firebaseUid") String firebaseUid, @Param("email") String email,
            @Param("dataRegion") String dataRegion, @Param("country") String country,
            @Param("currency") String currency, @Param("timeZone") String timeZone);
}
