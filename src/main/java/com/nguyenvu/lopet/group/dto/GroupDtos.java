package com.nguyenvu.lopet.group.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.nguyenvu.lopet.group.entity.GroupMemberRole;
import com.nguyenvu.lopet.group.entity.GroupMemberStatus;
import com.nguyenvu.lopet.group.entity.GroupType;

public final class GroupDtos {

    public record CreateGroupResponse(
            Integer id,
            String name,
            GroupType type,
            Integer ownerAccountId,
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

    public record ModifyGroupResponse(Integer id, boolean success) {
    }

    /** Dùng chung cho owned / joined / suggest — cả ba đều dựng cùng một bộ trường */
    public record GroupSummary(
            Integer id,
            String name,
            Integer ownerAccountId,
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
     *   <li>{@code viewerStatus} — quan hệ của người đang xem với nhóm, là thứ quyết định nút hiển thị
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
     * Quan hệ của người đang xem với nhóm. {@code NONE} cũng là giá trị trả về cho khách chưa đăng
     * nhập — cả hai trường hợp đều "chưa dính gì tới nhóm này", nên gộp lại thay vì thêm một giá
     * trị mà client phải xử lý y hệt.
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
            Integer accountId,
            GroupMemberAccount account,
            GroupMemberRole role,
            LocalDateTime joinedAt) {
    }

    /**
     * Thành viên hiển thị bằng hồ sơ tài khoản. Route {@code GET /v1/groups/:id} không xác thực, nên
     * nó cố ý KHÔNG trả email như {@code AccountBrief} từng làm — danh sách thành viên của một nhóm
     * công khai không phải chỗ để lộ địa chỉ liên lạc của từng người.
     */
    public record GroupMemberAccount(
            Integer accountId,
            String username,
            String fullName,
            String avatarUrl) {
    }

    /** Body dùng chung cho các hành động chỉ cần biết nhóm nào: trả lời lời mời, huỷ yêu cầu */
    public record GroupIdRequest(Integer groupId) {
    }

    /** {@code accountId} là người có yêu cầu đang chờ được duyệt hoặc bị từ chối */
    public record ReviewMemberRequest(Integer groupId, Integer accountId) {
    }

    /**
     * {@code status} là ACTIVE khi vào nhóm được ngay (nhóm PUBLIC) và PENDING khi phải chờ duyệt
     * (nhóm PRIVATE). Client đọc khoá này để biết nên hiện "Đã tham gia" hay "Đã gửi yêu cầu" mà
     * không cần gọi lại chi tiết nhóm.
     */
    public record JoinGroupResponse(Integer groupId, Integer accountId, GroupMemberStatus status) {
    }

    public record LeaveGroupResponse(Integer groupId, Integer accountId) {
    }

    /** {@code status} luôn là PENDING: lời mời chỉ thành thành viên sau khi người được mời chấp nhận */
    public record InviteResponse(Integer groupId, Integer inviteeAccountId, GroupMemberStatus status) {
    }

    /** Một người đang chờ được duyệt vào nhóm. {@code requestedAt} là lúc gửi yêu cầu. */
    public record PendingMemberView(
            Integer groupId,
            Integer accountId,
            GroupMemberAccount account,
            LocalDateTime requestedAt) {
    }

    /**
     * Một lời mời đang chờ trả lời. Kèm tên nhóm để hộp thư mời không phải gọi thêm một vòng chi
     * tiết nhóm cho từng dòng.
     *
     * <p>{@code invitedBy} có thể là object rỗng nếu người đã mời sau đó bị xoá — cột
     * {@code invited_by} cố ý không có khoá ngoại, xem {@code GroupMember}.
     */
    public record PendingInviteView(
            Integer groupId,
            String groupName,
            GroupType groupType,
            Integer accountId,
            GroupMemberAccount invitedBy,
            LocalDateTime invitedAt) {
    }

    private GroupDtos() {
    }
}
