package com.nguyenvu.lopet.advertisement.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.advertisement.entity.Advertisement;

public interface AdvertisementRepository extends JpaRepository<Advertisement, Integer> {

    /** Luôn nạp kèm advertiser + account để dựng được {@code author} trong DTO */
    @Query("""
            select a from Advertisement a
            join fetch a.advertiser adv
            join fetch adv.account
            where a.id = :id
            """)
    Optional<Advertisement> findDetailById(@Param("id") Integer id);

    @Query("""
            select a from Advertisement a
            join fetch a.advertiser adv
            join fetch adv.account acc
            where :accountId is null or acc.id = :accountId
            """)
    List<Advertisement> findAllDetail(@Param("accountId") Integer accountId);
}
