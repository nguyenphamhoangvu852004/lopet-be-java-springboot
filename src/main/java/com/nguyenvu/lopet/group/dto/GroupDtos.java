package com.nguyenvu.lopet.group.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.nguyenvu.lopet.group.entity.GroupMemberRole;
import com.nguyenvu.lopet.group.entity.GroupMemberStatus;
import com.nguyenvu.lopet.group.entity.GroupType;

/**
 * <b>Thành viên nhóm là THÚ CƯNG.</b> Sau khi khoá chính của {@code group_members} đổi từ
 * {@code (group_id, account_id)} sang {@code (group_id, pet_id)}, mọi khoá {@code accountId} ở đây
 * thành {@code petId} và {@code account} thành {@code pet}. Giữ tên cũ sẽ khiến client gửi id tài
 * khoản vào chỗ đang chờ id thú cưng — hai bên đều là số nguyên nên không có tầng nào bắt được.
 */
public final class GroupDtos {

    public record CreateGroupRequest(String name, String type, String bio) {
    }

    public record CreateGroupResponse(
            Integer id,
            String name,
            GroupType type,
            Integer ownerPetId,
            String bio,
            String coverUrl,
            LocalDateTime createdAt) {
    }

    /**
     * {@code invitee} là id THÚ CƯNG được mời, không phải id tài khoản. Phản hồi là
     * {@link InviteResponse}: từ khi mời tạo một lời mời chờ trả lời thay vì thêm thẳng thành viên,
     * client cần biết {@code status} nên {@code AddMemberResponse} cũ không còn đủ.
     */
    public record AddMemberRequest(Integer groupId, Integer invitee) {
    }

    /** {@code member} là id THÚ CƯNG bị xoá khỏi nhóm */
    public record RemoveMemberRequest(Integer groupId, Integer member) {
    }

    public record RemoveMemberResponse(Integer groupId, Integer member) {
    }

    public record DeleteGroupRequest(Integer groupId) {
    }

    /** {@code owner} không bao giờ được gán bên TS nên khoá đó vắng mặt trong JSON */
    public record DeleteGroupResponse(Integer groupId) {
    }

    public record ModifyGroupRequest(String name, String type, String bio) {
    }

    public record ModifyGroupResponse(Integer id, boolean success) {
    }

    /** Dùng chung cho owned / joined / suggest — cả ba đều dựng cùng một bộ trường */
    public record GroupSummary(
            Integer id,
            String name,
            Integer ownerPetId,
            String bio,
            String coverUrl,
            GroupType type,
            Integer totalMembers,
            LocalDateTime createdAt) {
    }

    /**
     * GET /v1/groups/:id. Ba khoá cuối là tầng riêng tư của nhóm:
     *
     * <ul>
     *   <li>{@code totalMembers} — luôn đếm ĐÚNG số thành viên ACTIVE, kể cả khi {@code members} bị
     *       che. Con số này không bí mật và giao diện cần nó để mô tả nhóm.
     *   <li>{@code restricted} — true nghĩa là {@code members} đã bị rút thành danh sách rỗng vì
     *       người xem không phải thành viên của một nhóm PRIVATE. Client phải đọc cờ này chứ không
     *       suy ra từ {@code members.isEmpty()}: một nhóm PUBLIC rỗng cũng cho danh sách rỗng.
     *   <li>{@code viewerStatus} — quan hệ của PET đang xem với nhóm, là thứ quyết định nút hiển thị
     *       là "Tham gia", "Đã gửi yêu cầu" hay "Rời nhóm".
     * </ul>
     */
    public record GroupDetail(
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime deletedAt,
            Integer id,
            String name,
            GroupType type,
            String bio,
            String coverUrl,
            List<GroupMemberView> members,
            Integer totalMembers,
            boolean restricted,
            ViewerStatus viewerStatus) {
    }

    /**
     * Quan hệ của pet đang xem với nhóm. {@code NONE} cũng là giá trị trả về cho khách chưa đăng
     * nhập hoặc người đã đăng nhập mà chưa chọn pet — cả ba trường hợp đều "chưa dính gì tới nhóm
     * này", nên gộp lại thay vì thêm một giá trị mà client phải xử lý y hệt.
     */
    public enum ViewerStatus {
        NONE,
        PENDING_REQUEST,
        PENDING_INVITE,
        MEMBER
    }

    /** Quan hệ {@code group} không được nạp kèm bên TS nên khoá đó vắng mặt */
    public record GroupMemberView(
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime deletedAt,
            Integer groupId,
            Integer petId,
            GroupMemberPet pet,
            GroupMemberRole role,
            LocalDateTime joinedAt) {
    }

    /**
     * Thành viên hiển thị bằng hồ sơ CÔNG KHAI của thú cưng. Route {@code GET /v1/groups/:id} không
     * xác thực, nên nó không được để lộ tài khoản đứng sau từng thành viên — trước đây nó trả nguyên
     * {@code AccountBrief} kèm email.
     */
    public record GroupMemberPet(
            Integer petId,
            String name,
            String handle,
            String displayName,
            String avatarUrl) {
    }

    /** Body dùng chung cho các hành động chỉ cần biết nhóm nào: trả lời lời mời, huỷ yêu cầu */
    public record GroupIdRequest(Integer groupId) {
    }

    /** {@code petId} là THÚ CƯNG có yêu cầu đang chờ được duyệt hoặc bị từ chối */
    public record ReviewMemberRequest(Integer groupId, Integer petId) {
    }

    /**
     * {@code status} là ACTIVE khi vào nhóm được ngay (nhóm PUBLIC) và PENDING khi phải chờ duyệt
     * (nhóm PRIVATE). Client đọc khoá này để biết nên hiện "Đã tham gia" hay "Đã gửi yêu cầu" mà
     * không cần gọi lại chi tiết nhóm.
     */
    public record JoinGroupResponse(Integer groupId, Integer petId, GroupMemberStatus status) {
    }

    public record LeaveGroupResponse(Integer groupId, Integer petId) {
    }

    /** {@code status} luôn là PENDING: lời mời chỉ thành thành viên sau khi chính pet đó chấp nhận */
    public record InviteResponse(Integer groupId, Integer inviteePetId, GroupMemberStatus status) {
    }

    /** Một pet đang chờ được duyệt vào nhóm. {@code requestedAt} là lúc gửi yêu cầu. */
    public record PendingMemberView(
            Integer groupId,
            Integer petId,
            GroupMemberPet pet,
            LocalDateTime requestedAt) {
    }

    /**
     * Một lời mời đang chờ pet trả lời. Kèm tên nhóm để hộp thư mời không phải gọi thêm một vòng
     * chi tiết nhóm cho từng dòng.
     *
     * <p>{@code invitedBy} có thể là object rỗng nếu pet đã mời sau đó ngừng hoạt động — cột
     * {@code invited_by} cố ý không có khoá ngoại, xem {@code GroupMember}.
     */
    public record PendingInviteView(
            Integer groupId,
            String groupName,
            GroupType groupType,
            Integer petId,
            GroupMemberPet invitedBy,
            LocalDateTime invitedAt) {
    }

    private GroupDtos() {
    }
}
