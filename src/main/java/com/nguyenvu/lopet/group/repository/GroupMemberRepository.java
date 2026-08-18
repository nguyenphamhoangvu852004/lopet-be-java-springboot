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
 * Đơn vị thành viên là THÚ CƯNG: khoá tra cứu là {@code (groupId, petId)} chứ không còn
 * {@code (groupId, accountId)}. Mọi lời gọi phải truyền petId lấy từ {@code PetContext}, không bao
 * giờ là id tài khoản trong token — hai con số đều là {@code Integer} nên trình biên dịch không bắt
 * được nhầm lẫn này, chỉ tên tham số mới cảnh báo được.
 *
 * <p><b>Hai họ truy vấn, không được lẫn.</b> Từ khi {@code group_members} có cột {@code status},
 * "tồn tại hàng" không còn là "đang là thành viên":
 *
 * <ul>
 *   <li>{@code findActive*} / {@code count*} — chỉ hàng ACTIVE. <b>Mọi câu hỏi phân quyền và mọi
 *       danh sách hiển thị phải dùng nhóm này.</b>
 *   <li>{@code findByGroupIdAndPetId} và {@code findPending*} — đọc được cả hàng PENDING, chỉ dành
 *       cho luồng xin vào / mời / duyệt, nơi sự tồn tại của hàng chờ mới là thông tin cần biết.
 * </ul>
 *
 * <p>Không có finder nào bỏ lọc {@code status} mà lại được dùng để trả lời "có được xem/đăng/quản
 * trị không" — hàng PENDING sinh ra từ hành động tự nguyện của người ngoài, nên nhầm một chỗ là mở
 * cửa nhóm PRIVATE cho bất kỳ ai.
 */
public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {

    /**
     * Tra cứu THÔ, mọi trạng thái. Chỉ dùng để phát hiện "đã có hàng nào cho cặp này chưa" trong
     * luồng xin vào/mời. KHÔNG dùng cho phân quyền — dùng {@link #findActiveByGroupIdAndPetId}.
     */
    Optional<GroupMember> findByGroupIdAndPetId(Integer groupId, Integer petId);

    @Query("""
            select m from GroupMember m
            where m.groupId = :groupId and m.petId = :petId
              and m.status = com.nguyenvu.lopet.group.entity.GroupMemberStatus.ACTIVE
            """)
    Optional<GroupMember> findActiveByGroupIdAndPetId(@Param("groupId") Integer groupId,
                                                      @Param("petId") Integer petId);

    @Query("""
            select count(m) from GroupMember m
            where m.groupId = :groupId and m.petId = :petId and m.role = :role
              and m.status = com.nguyenvu.lopet.group.entity.GroupMemberStatus.ACTIVE
            """)
    long countActiveByGroupIdAndPetIdAndRole(@Param("groupId") Integer groupId,
                                             @Param("petId") Integer petId,
                                             @Param("role") GroupMemberRole role);

    /** OWNER hoặc ADMIN — dùng cho duyệt yêu cầu, xoá thành viên và sửa thông tin nhóm */
    @Query("""
            select count(m) from GroupMember m
            where m.groupId = :groupId and m.petId = :petId
              and m.status = com.nguyenvu.lopet.group.entity.GroupMemberStatus.ACTIVE
              and m.role in (com.nguyenvu.lopet.group.entity.GroupMemberRole.OWNER,
                             com.nguyenvu.lopet.group.entity.GroupMemberRole.ADMIN)
            """)
    long countManagers(@Param("groupId") Integer groupId, @Param("petId") Integer petId);

    @Query("""
            select count(m) from GroupMember m
            where m.groupId = :groupId
              and m.status = com.nguyenvu.lopet.group.entity.GroupMemberStatus.ACTIVE
            """)
    long countActiveMembers(@Param("groupId") Integer groupId);

    /** Người nhận thông báo khi có yêu cầu vào nhóm mới */
    @Query("""
            select m.petId from GroupMember m
            where m.groupId = :groupId
              and m.status = com.nguyenvu.lopet.group.entity.GroupMemberStatus.ACTIVE
              and m.role in (com.nguyenvu.lopet.group.entity.GroupMemberRole.OWNER,
                             com.nguyenvu.lopet.group.entity.GroupMemberRole.ADMIN)
            """)
    List<Integer> findActiveManagerPetIds(@Param("groupId") Integer groupId);

    @Query("""
            select m from GroupMember m
            where m.petId = :petId
              and m.status = com.nguyenvu.lopet.group.entity.GroupMemberStatus.ACTIVE
            """)
    List<GroupMember> findActiveByPetId(@Param("petId") Integer petId);

    @Query("""
            select m from GroupMember m
            where m.petId = :petId and m.role = :role
              and m.status = com.nguyenvu.lopet.group.entity.GroupMemberStatus.ACTIVE
            """)
    List<GroupMember> findActiveByPetIdAndRole(@Param("petId") Integer petId,
                                               @Param("role") GroupMemberRole role);

    /**
     * Nhóm mà BẤT KỲ thú cưng nào của một tài khoản đang tham gia — dùng cho các route liệt kê theo
     * tài khoản. Đi qua {@code m.pet.account.id} thay vì giữ lại một cột {@code account_id} trên
     * bảng nối: cột đó sẽ nói dối ngay khi pet đổi chủ.
     */
    @Query("""
            select m from GroupMember m
            where m.pet.account.id = :accountId
              and m.status = com.nguyenvu.lopet.group.entity.GroupMemberStatus.ACTIVE
            """)
    List<GroupMember> findByOwnerAccountId(@Param("accountId") Integer accountId);

    @Query("""
            select m from GroupMember m
            where m.pet.account.id = :accountId and m.role = :role
              and m.status = com.nguyenvu.lopet.group.entity.GroupMemberStatus.ACTIVE
            """)
    List<GroupMember> findByOwnerAccountIdAndRole(@Param("accountId") Integer accountId,
                                                  @Param("role") GroupMemberRole role);

    /**
     * Hộp thư của quản trị nhóm: các pet tự xin vào và đang chờ duyệt.
     * {@code invitedByPetId is null} là thứ phân biệt chúng với lời mời — xem
     * {@code GroupMemberStatus}.
     */
    @Query("""
            select m from GroupMember m
            where m.groupId = :groupId
              and m.status = com.nguyenvu.lopet.group.entity.GroupMemberStatus.PENDING
              and m.invitedByPetId is null
            order by m.createdAt
            """)
    List<GroupMember> findPendingRequests(@Param("groupId") Integer groupId);

    /** Hộp thư của pet: các lời mời chưa trả lời */
    @Query("""
            select m from GroupMember m
            where m.petId = :petId
              and m.status = com.nguyenvu.lopet.group.entity.GroupMemberStatus.PENDING
              and m.invitedByPetId is not null
            order by m.createdAt
            """)
    List<GroupMember> findPendingInvitesForPet(@Param("petId") Integer petId);

    @Query("""
            select m from GroupMember m
            where m.groupId = :groupId and m.petId = :petId
              and m.status = com.nguyenvu.lopet.group.entity.GroupMemberStatus.PENDING
            """)
    Optional<GroupMember> findPendingByGroupIdAndPetId(@Param("groupId") Integer groupId,
                                                       @Param("petId") Integer petId);

    void deleteByGroupIdAndPetId(Integer groupId, Integer petId);
}
