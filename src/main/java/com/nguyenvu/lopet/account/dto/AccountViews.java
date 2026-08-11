package com.nguyenvu.lopet.account.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.nguyenvu.lopet.friendship.entity.FriendshipStatus;
import com.nguyenvu.lopet.role.entity.RoleName;

/**
 * Các "entity view" — hình dạng JSON mà TypeORM sinh ra khi controller trả thẳng entity thay vì DTO.
 *
 * <p>GET /v1/accounts, PUT /v1/accounts và GET /v1/accounts/suggest/:id đều rơi vào trường hợp đó,
 * nên hình dạng response phụ thuộc chính xác vào những quan hệ nào được nạp kèm: quan hệ không nạp
 * thì khoá đó KHÔNG xuất hiện trong JSON, chứ không phải xuất hiện với giá trị null.
 */
public final class AccountViews {

    /** Account không nạp quan hệ nào — dùng cho suggest và cho hai đầu của friendship */
    public record AccountBrief(
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime deletedAt,
            Integer id,
            String email,
            String username,
            Integer isBanned) {
    }

    public record ProfileView(
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime deletedAt,
            Integer id,
            String fullName,
            String phoneNumber,
            String bio,
            Integer sex,
            LocalDate dateOfBirth,
            String hometown,
            String avatarUrl,
            String coverUrl) {
    }

    public record RoleView(
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime deletedAt,
            Integer id,
            RoleName name,
            String description) {
    }

    /**
     * Quan hệ {@code account} và {@code grantedBy} cố ý vắng mặt: bên TS chỉ nạp
     * {@code accountRoles: { role: true }}, nên hai khoá đó không có trong JSON.
     */
    public record AccountRoleView(
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime deletedAt,
            Integer accountId,
            Integer roleId,
            RoleView role,
            LocalDateTime grantedAt) {
    }

    public record FriendshipView(
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime deletedAt,
            Integer id,
            AccountBrief sender,
            AccountBrief receiver,
            FriendshipStatus status) {
    }

    /** Phần tử của GET /v1/accounts — nạp profile, accountRoles.role và cả hai chiều friendship */
    public record AccountListItem(
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime deletedAt,
            Integer id,
            String email,
            String username,
            Integer isBanned,
            ProfileView profile,
            List<AccountRoleView> accountRoles,
            List<FriendshipView> sentFriendRequests,
            List<FriendshipView> receivedFriendRequests) {
    }

    /** Kết quả của PUT /v1/accounts — repo trả về bản findById (profile + accountRoles.role) */
    public record AccountDetail(
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime deletedAt,
            Integer id,
            String email,
            String username,
            Integer isBanned,
            ProfileView profile,
            List<AccountRoleView> accountRoles) {
    }

    private AccountViews() {
    }
}
