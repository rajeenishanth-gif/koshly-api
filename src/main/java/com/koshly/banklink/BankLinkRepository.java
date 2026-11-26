package com.koshly.banklink;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Like AccountRepository: every query is limited to one user. */
public interface BankLinkRepository extends JpaRepository<BankLink, UUID> {

    @Query("""
            select l from BankLink l
            where l.userId = :userId and l.country = :country and l.removedAt is null
            order by l.createdAt
            """)
    List<BankLink> findAllForUser(@Param("userId") String userId, @Param("country") String country);

    @Query("select l from BankLink l where l.id = :id and l.userId = :userId and l.removedAt is null")
    Optional<BankLink> findForUser(@Param("id") UUID id, @Param("userId") String userId);
}
