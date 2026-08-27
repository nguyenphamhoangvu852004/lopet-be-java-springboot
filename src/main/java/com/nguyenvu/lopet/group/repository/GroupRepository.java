package com.nguyenvu.lopet.group.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.group.entity.Group;

public interface GroupRepository extends JpaRepository<Group, Integer> {

    /**
     * Nạp kèm {@code members.account.accountProfile}: danh sách thành viên hiển thị hồ sơ của từng
     * người, và hai cấp quan hệ này đều lazy nên bỏ fetch là N+1 theo số thành viên.
     */
    @Query("""
            select distinct gr from Group gr
            left join fetch gr.members m
            left join fetch m.account mp
            left join fetch mp.accountProfile
            where gr.id = :id
            """)
    Optional<Group> findDetailById(@Param("id") Integer id);

    /**
     * Lọc theo group_members trước rồi mới nạp group theo id. Không lọc trực tiếp trong mệnh đề
     * where của truy vấn nạp group, vì điều kiện đó sẽ cắt luôn danh sách members được nạp kèm và
     * làm {@code totalMembers} bị sai.
     */
    @Query("""
            select distinct gr from Group gr
            left join fetch gr.members m
            left join fetch m.account mp
            left join fetch mp.accountProfile
            where gr.id in :ids
            """)
    List<Group> findAllDetailByIds(@Param("ids") List<Integer> ids);

    /** ORDER BY RAND() giữ nguyên — tính ngẫu nhiên là hành vi của endpoint gợi ý nhóm */
    @Query(value = "select id from `groups` where deletedAt is null order by rand() limit 10",
            nativeQuery = true)
    List<Integer> findSuggestIds();
}
