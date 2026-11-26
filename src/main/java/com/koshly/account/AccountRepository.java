package com.koshly.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every method takes the owner's user ID. There is deliberately no
 * "find by id alone" used anywhere, so an account can only ever be loaded
 * for the user who owns it.
 *
 * "left join fetch" loads each account's card or loan details in the same
 * query, instead of one extra query per account.
 */
public interface AccountRepository extends JpaRepository<Account, UUID> {

    @Query("""
            select a from Account a
            left join fetch a.creditCard
            left join fetch a.loan
            where a.userId = :userId
            order by a.type, a.name
            """)
    List<Account> findAllForUser(@Param("userId") String userId);

    @Query("""
            select a from Account a
            left join fetch a.creditCard
            left join fetch a.loan
            where a.id = :id and a.userId = :userId
            """)
    Optional<Account> findForUser(@Param("id") UUID id, @Param("userId") String userId);

    long countByUserIdAndType(String userId, String type);
}
