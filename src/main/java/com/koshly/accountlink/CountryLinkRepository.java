package com.koshly.accountlink;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CountryLinkRepository extends JpaRepository<CountryLink, String> {

    /** The person's account in that country, if they added one. */
    Optional<CountryLink> findByHomeUserIdAndLinkedCountry(String homeUserId, String linkedCountry);

    /** Every country the person added. */
    List<CountryLink> findByHomeUserId(String homeUserId);

    /** When an added account is deleted (deleting the home account removes its rows by cascade). */
    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM country_link WHERE linked_user_id = :uid", nativeQuery = true)
    int deleteByLinkedUserId(@Param("uid") String uid);
}
