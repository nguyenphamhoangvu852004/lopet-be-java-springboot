package com.nguyenvu.lopet.account.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.account.entity.AccountRole;
import com.nguyenvu.lopet.account.entity.AccountRoleId;

public interface AccountRoleRepository extends JpaRepository<AccountRole, AccountRoleId> {

    List<AccountRole> findByAccountId(Integer accountId);

    @Modifying
    @Query("delete from AccountRole ar where ar.accountId = :accountId")
    void deleteByAccountId(@Param("accountId") Integer accountId);
}
