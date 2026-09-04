package com.nguyenvu.lopet.accountprofile.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.accountprofile.entity.AccountProfile;

public interface AccountProfileRepository extends JpaRepository<AccountProfile, Integer> {

    @Query("select p from AccountProfile p where p.account.id = :accountId")
    Optional<AccountProfile> findByAccountId(@Param("accountId") Integer accountId);

    /**
     * Bản nạp kèm tài khoản, dùng cho hồ sơ của NGƯỜI KHÁC.
     *
     * Không còn lọc theo người xem: hồ sơ nào cũng đọc được. Vẫn tách khỏi
     * {@link #findByAccountId} vì chỗ gọi cần `p.account` đã nạp sẵn.
     */
    @Query("select p from AccountProfile p left join fetch p.account a where a.id = :accountId")
    Optional<AccountProfile> findWithAccountByAccountId(@Param("accountId") Integer accountId);
}
