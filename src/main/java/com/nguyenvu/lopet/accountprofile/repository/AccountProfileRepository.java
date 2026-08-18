package com.nguyenvu.lopet.accountprofile.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.accountprofile.entity.AccountProfile;

/**
 * Một hàm duy nhất, và nó tra bằng {@code accountId} chứ không phải id hồ sơ: sau refactor không
 * luồng nào còn nhận id hồ sơ từ client, nên không tồn tại tham số để trỏ sang hồ sơ của người khác.
 */
public interface AccountProfileRepository extends JpaRepository<AccountProfile, Integer> {

    @Query("select p from AccountProfile p where p.account.id = :accountId")
    Optional<AccountProfile> findByAccountId(@Param("accountId") Integer accountId);
}
