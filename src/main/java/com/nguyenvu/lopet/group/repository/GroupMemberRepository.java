package com.nguyenvu.lopet.group.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.group.entity.GroupMember;
import com.nguyenvu.lopet.group.entity.GroupMemberId;
import com.nguyenvu.lopet.group.entity.GroupMemberRole;

/**
 * Khoá tra cứu là {@code (groupId, accountId)}.
 *
 * <p><b>Hai họ truy vấn, không được lẫn.</b> Từ khi {@code group_members} có cột {@code status},
 * "tồn tại hàng" không còn là "đang là thành viên":
 *
 * <ul>
 *   <li>{@code findActive*} / {@code count*} — chỉ hàng ACTIVE. <b>Mọi câu hỏi phân quyền và mọi
 *       danh sách hiển thị phải dùng nhóm này.</b>
 *   <li>{@code findByGroupIdAndAccountId} và {@code findPending*} — đọc được cả hàng PENDING, chỉ
 *       dành cho luồng xin vào / mời / duyệt, nơi sự tồn tại của hàng chờ mới là thông tin cần biết.
 * </ul>
 *
 * <p>Không có finder nào bỏ lọc {@code status} mà lại được dùng để trả lời "có được xem/đăng/quản
 * trị không" — hàng PENDING sinh ra từ hành động tự nguyện của người ngoài, nên nhầm một chỗ là mở
 * cửa nhóm PRIVATE cho bất kỳ ai.
 */
public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {

    /**
     * Tra cứu THÔ, mọi trạng thái. Chỉ dùng để phát hiện "đã có hàng nào cho cặp này chưa" trong
     * luồng xin vào/mời. KHÔNG dùng cho phân quyền — dùng {@link #findActiveByGroupIdAndAccountId}.
     */
    Optional<GroupMember> findByGroupIdAndAccountId(Integer groupId, Integer accountId);

    @Query("""
            select m from GroupMember m
            where m.groupId = :groupId and m.accountId = :accountId
              and m.status = com.nguyenvu.lopet.group.entity.GroupMemberStatus.ACTIVE
            """)
    Optional<GroupMember> findActiveByGroupIdAndAccountId(@Param("groupId") Integer groupId,
                                                          @Param("accountId") Integer accountId);

    @Query("""
            select count(m) from GroupMember m
            where m.groupId = :groupId and m.accountId = :accountId and m.role = :role
              and m.status = com.nguyenvu.lopet.group.entity.GroupMemberStatus.ACTIVE
            """)
    long countActiveByGroupIdAndAccountIdAndRole(@Param("groupId") Integer groupId,
                                                  @Param("accountId") Integer accountId,
                                                  @Param("role") GroupMemberRole role);

    /** OWNER hoặc ADMIN — dùng cho duyệt yêu cầu, xoá thành viên và sửa thông tin nhóm */
    @Query("""
            select count(m) from GroupMember m
            where m.groupId = :groupId and m.accountId = :accountId
              and m.status = com.nguyenvu.lopet.group.entity.GroupMemberStatus.ACTIVE
              and m.role in (com.nguyenvu.lopet.group.entity.GroupMemberRole.OWNER,
                             com.nguyenvu.lopet.group.entity.GroupMemberRole.ADMIN)
            """)
    long countManagers(@Param("groupId") Integer groupId, @Param("accountId") Integer accountId);

    @Query("""
            select count(m) from GroupMember m
            where m.groupId = :groupId
              and m.status = com.nguyenvu.lopet.group.entity.GroupMemberStatus.ACTIVE
            """)
    long countActiveMembers(@Param("groupId") Integer groupId);

    /** Người nhận thông báo khi có yêu cầu vào nhóm mới */
    @Query("""
            select m.accountId from GroupMember m
            where m.groupId = :groupId
              and m.status = com.nguyenvu.lopet.group.entity.GroupMemberStatus.ACTIVE
              and m.role in (com.nguyenvu.lopet.group.entity.GroupMemberRole.OWNER,
                             com.nguyenvu.lopet.group.entity.GroupMemberRole.ADMIN)
            """)
    List<Integer> findActiveManagerAccountIds(@Param("groupId") Integer groupId);

    @Query("""
            select m from GroupMember m
            where m.accountId = :accountId
              and m.status = com.nguyenvu.lopet.group.entity.GroupMemberStatus.ACTIVE
            """)
    List<GroupMember> findActiveByAccountId(@Param("accountId") Integer accountId);

    @Query("""
            select m from GroupMember m
            where m.accountId = :accountId and m.role = :role
              and m.status = com.nguyenvu.lopet.group.entity.GroupMemberStatus.ACTIVE
            """)
    List<GroupMember> findActiveByAccountIdAndRole(@Param("accountId") Integer accountId,
                                                    @Param("role") GroupMemberRole role);

    /**
     * Hộp thư của quản trị nhóm: những người tự xin vào và đang chờ duyệt.
     * {@code invitedByAccountId is null} là thứ phân biệt chúng với lời mời — xem
     * {@code GroupMemberStatus}.
     */
    @Query("""
            select m from GroupMember m
            where m.groupId = :groupId
              and m.status = com.nguyenvu.lopet.group.entity.GroupMemberStatus.PENDING
              and m.invitedByAccountId is null
            order by m.createdAt
            """)
    List<GroupMember> findPendingRequests(@Param("groupId") Integer groupId);

    /** Hộp thư của người dùng: các lời mời chưa trả lời */
    @Query("""
            select m from GroupMember m
            where m.accountId = :accountId
              and m.status = com.nguyenvu.lopet.group.entity.GroupMemberStatus.PENDING
              and m.invitedByAccountId is not null
            order by m.createdAt
            """)
    List<GroupMember> findPendingInvitesForAccount(@Param("accountId") Integer accountId);

    @Query("""
            select m from GroupMember m
            where m.groupId = :groupId and m.accountId = :accountId
              and m.status = com.nguyenvu.lopet.group.entity.GroupMemberStatus.PENDING
            """)
    Optional<GroupMember> findPendingByGroupIdAndAccountId(@Param("groupId") Integer groupId,
                                                            @Param("accountId") Integer accountId);

    void deleteByGroupIdAndAccountId(Integer groupId, Integer accountId);
}
