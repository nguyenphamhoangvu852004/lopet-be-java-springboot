package com.nguyenvu.lopet.profile.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.profile.entity.Profile;

public interface ProfileRepository extends JpaRepository<Profile, Integer> {

    /** Nạp kèm account: tầng ownership cần biết profile này thuộc tài khoản nào */
    @EntityGraph(attributePaths = "account")
    @Query("select p from Profile p left join p.account a where p.id = :id")
    Optional<Profile> findDetailById(@Param("id") Integer id);

    @Query("select p from Profile p where p.account.id = :accountId")
    Optional<Profile> findByAccountId(@Param("accountId") Integer accountId);

    /**
     * Bộ lọc của GET /v1/profiles. {@code ILike} bên TypeORM dịch ra {@code LIKE} trên MySQL, và
     * collation mặc định vốn không phân biệt hoa thường — nên {@code like} ở đây cho cùng kết quả.
     */
    @Query("""
            select p from Profile p
            where (:id is null or p.id = :id)
              and (:fullName is null or lower(p.fullName) like lower(concat('%', :fullName, '%')))
            """)
    List<Profile> search(@Param("id") Integer id, @Param("fullName") String fullName);
}
