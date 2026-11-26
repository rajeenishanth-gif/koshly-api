package com.koshly.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every method takes the owner's user ID. There is deliberately no
 * "find by id alone" used anywhere, so an account can only ever be loaded
 * for the user who owns it.
 *
 * Removed accounts (removedAt set) are left out everywhere except
 * findRemovedForUser, which Undo uses.
 *
 * "left join fetch" loads each account's card or loan details in the same
 * query, instead of one extra query per account.
 */
public interface AccountRepository extends JpaRepository<Account, UUID> {

    @Query("""
            select a from Account a
            left join fetch a.creditCard
            left join fetch a.loan
            where a.userId = :userId and a.country = :country and a.removedAt is null
            order by a.type, a.name
            """)
    List<Account> findAllForUser(@Param("userId") String userId, @Param("country") String country);

    @Query("""
            select a from Account a
            left join fetch a.creditCard
            left join fetch a.loan
            where a.id = :id and a.userId = :userId and a.removedAt is null
            """)
    Optional<Account> findForUser(@Param("id") UUID id, @Param("userId") String userId);

    @Query("""
            select a from Account a
            left join fetch a.creditCard
            left join fetch a.loan
            where a.id = :id and a.userId = :userId and a.removedAt is not null
            """)
    Optional<Account> findRemovedForUser(@Param("id") UUID id, @Param("userId") String userId);

    long countByUserIdAndTypeAndRemovedAtIsNull(String userId, String type);

    /**
     * Deletes accounts removed before the cutoff. Plain SQL so the database's
     * ON DELETE CASCADE also deletes their card and loan details.
     */
    @Modifying
    @Query(value = "DELETE FROM account WHERE removed_at < :cutoff", nativeQuery = true)
    int deleteRemovedBefore(@Param("cutoff") Instant cutoff);
}
