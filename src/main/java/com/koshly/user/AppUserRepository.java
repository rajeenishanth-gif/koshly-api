package com.koshly.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByFirebaseUid(String firebaseUid);

    /**
     * Creates the user's row the first time they save something. If it already
     * exists, does nothing. Safe even if two requests arrive at the same moment.
     */
    @Modifying
    @Query(value = """
            INSERT INTO app_user (firebase_uid, email, data_region)
            VALUES (:firebaseUid, :email, :dataRegion)
            ON CONFLICT (firebase_uid) DO NOTHING
            """, nativeQuery = true)
    void insertIfMissing(@Param("firebaseUid") String firebaseUid, @Param("email") String email,
            @Param("dataRegion") String dataRegion);
}
