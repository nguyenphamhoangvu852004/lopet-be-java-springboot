package com.nguyenvu.lopet.accountprofile.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.accountprofile.entity.AccountProfile;

public interface AccountProfileRepository extends JpaRepository<AccountProfile, Integer> {

    @Query("select p from AccountProfile p where p.account.id = :accountId")
    Optional<AccountProfile> findByAccountId(@Param("accountId") Integer accountId);

    @Query("select p from AccountProfile p left join p.account a "
            + " where a.id = :accountId and " + AccountProfileVisibilityFilter.VISIBLE_TO)
    Optional<AccountProfile> findVisibleByAccountId(@Param("accountId") Integer accountId,
                                                    @Param("viewerId") Integer viewerId);
}
