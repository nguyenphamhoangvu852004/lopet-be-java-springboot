package com.nguyenvu.lopet.account.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.nguyenvu.lopet.friendship.entity.FriendshipStatus;
import com.nguyenvu.lopet.role.entity.RoleName;

public final class AccountViews {

    public record AccountBrief(
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime deletedAt,
            Integer id,
            String email,
            String username,
            Integer isBanned) {
    }

    public record AccountProfileView(
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

    public record AccountListItem(
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime deletedAt,
            Integer id,
            String email,
            String username,
            Integer isBanned,
            AccountProfileView profile,
            List<AccountRoleView> accountRoles,
            List<FriendshipView> sentFriendRequests,
            List<FriendshipView> receivedFriendRequests) {
    }

    public record AccountDetail(
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime deletedAt,
            Integer id,
            String email,
            String username,
            Integer isBanned,
            AccountProfileView profile,
            List<AccountRoleView> accountRoles) {
    }

    private AccountViews() {
    }
}
