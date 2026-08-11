package com.nguyenvu.lopet.advertiser.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.advertiser.entity.AdvertiserProfile;

public interface AdvertiserProfileRepository extends JpaRepository<AdvertiserProfile, Integer> {

    @Query("select p from AdvertiserProfile p left join fetch p.account where p.id = :id")
    Optional<AdvertiserProfile> findDetailById(@Param("id") Integer id);

    @Query("select p from AdvertiserProfile p left join fetch p.account where p.account.id = :accountId")
    Optional<AdvertiserProfile> findByAccountId(@Param("accountId") Integer accountId);

    @Query("""
            select p from AdvertiserProfile p
            left join fetch p.account
            left join fetch p.approvedBy
            order by p.createdAt desc
            """)
    List<AdvertiserProfile> findAllDetail();
}
