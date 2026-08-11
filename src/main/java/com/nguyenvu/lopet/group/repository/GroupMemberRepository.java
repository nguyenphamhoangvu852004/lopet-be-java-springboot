package com.nguyenvu.lopet.group.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.group.entity.GroupMember;
import com.nguyenvu.lopet.group.entity.GroupMemberId;
import com.nguyenvu.lopet.group.entity.GroupMemberRole;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {

    Optional<GroupMember> findByGroupIdAndAccountId(Integer groupId, Integer accountId);

    long countByGroupIdAndAccountIdAndRole(Integer groupId, Integer accountId, GroupMemberRole role);

    /** OWNER hoặc ADMIN — dùng cho mời/xoá thành viên và sửa thông tin nhóm */
    @Query("""
            select count(m) from GroupMember m
            where m.groupId = :groupId and m.accountId = :accountId
              and m.role in (com.nguyenvu.lopet.group.entity.GroupMemberRole.OWNER,
                             com.nguyenvu.lopet.group.entity.GroupMemberRole.ADMIN)
            """)
    long countManagers(@Param("groupId") Integer groupId, @Param("accountId") Integer accountId);

    List<GroupMember> findByAccountId(Integer accountId);

    List<GroupMember> findByAccountIdAndRole(Integer accountId, GroupMemberRole role);

    void deleteByGroupIdAndAccountId(Integer groupId, Integer accountId);
}
