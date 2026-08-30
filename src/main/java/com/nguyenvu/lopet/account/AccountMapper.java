package com.nguyenvu.lopet.account;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.nguyenvu.lopet.account.dto.AccountViews;
import com.nguyenvu.lopet.account.dto.AccountViews.AccountBrief;
import com.nguyenvu.lopet.account.dto.AccountViews.AccountRoleView;
import com.nguyenvu.lopet.account.dto.AccountViews.FriendshipView;
import com.nguyenvu.lopet.account.dto.AccountViews.AccountProfileView;
import com.nguyenvu.lopet.account.dto.AccountViews.RoleView;
import com.nguyenvu.lopet.account.dto.GetAccountResponse;
import com.nguyenvu.lopet.account.dto.GetAccountResponse.AccountProfileSummary;
import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.entity.AccountRole;
import com.nguyenvu.lopet.friendship.entity.Friendship;
import com.nguyenvu.lopet.accountprofile.entity.AccountProfile;
import com.nguyenvu.lopet.role.entity.Role;

public final class AccountMapper {

    public static GetAccountResponse toGetAccountResponse(Account account) {
        List<String> roles = account.getAccountRoles().stream()
                .map(accountRole -> accountRole.getRole().getName().name())
                .toList();

        AccountProfile profile = account.getAccountProfile();
        AccountProfileSummary summary = profile == null ? null : new AccountProfileSummary(
                profile.getId(),
                profile.getFullName(),
                profile.getBio(),
                profile.getPhoneNumber(),
                profile.getAvatarUrl(),
                profile.getCoverUrl());

        return new GetAccountResponse(account.getId(), account.getEmail(), account.getUsername(), roles, summary);
    }

    public static AccountBrief toBrief(Account account) {
        if (account == null) {
            return null;
        }
        return new AccountBrief(account.getCreatedAt(), account.getUpdatedAt(), account.getDeletedAt(),
                account.getId(), account.getEmail(), account.getUsername(), account.getIsBanned());
    }

    public static AccountProfileView toAccountProfileView(AccountProfile profile) {
        if (profile == null) {
            return null;
        }
        return new AccountProfileView(profile.getCreatedAt(), profile.getUpdatedAt(), profile.getDeletedAt(),
                profile.getId(), profile.getFullName(), profile.getPhoneNumber(), profile.getBio(),
                profile.getSex(), profile.getDateOfBirth(), profile.getHometown(),
                profile.getAvatarUrl(), profile.getCoverUrl());
    }

    public static RoleView toRoleView(Role role) {
        if (role == null) {
            return null;
        }
        return new RoleView(role.getCreatedAt(), role.getUpdatedAt(), role.getDeletedAt(),
                role.getId(), role.getName(), role.getDescription());
    }

    public static AccountRoleView toAccountRoleView(AccountRole accountRole) {
        return new AccountRoleView(accountRole.getCreatedAt(), accountRole.getUpdatedAt(),
                accountRole.getDeletedAt(), accountRole.getAccountId(), accountRole.getRoleId(),
                toRoleView(accountRole.getRole()), accountRole.getGrantedAt());
    }

    public static FriendshipView toFriendshipView(Friendship friendship) {
        return new FriendshipView(friendship.getCreatedAt(), friendship.getUpdatedAt(),
                friendship.getDeletedAt(), friendship.getId(),
                toBrief(friendship.getSender()), toBrief(friendship.getReceiver()),
                friendship.getStatus());
    }

    public static AccountViews.AccountDetail toDetail(Account account) {
        return new AccountViews.AccountDetail(account.getCreatedAt(), account.getUpdatedAt(),
                account.getDeletedAt(), account.getId(), account.getEmail(), account.getUsername(),
                account.getIsBanned(), toAccountProfileView(account.getAccountProfile()), sortedRoles(account));
    }

    public static AccountViews.AccountListItem toListItem(Account account,
                                                          Map<Integer, List<Friendship>> sentByAccount,
                                                          Map<Integer, List<Friendship>> receivedByAccount) {
        return new AccountViews.AccountListItem(account.getCreatedAt(), account.getUpdatedAt(),
                account.getDeletedAt(), account.getId(), account.getEmail(), account.getUsername(),
                account.getIsBanned(), toAccountProfileView(account.getAccountProfile()), sortedRoles(account),
                friendshipViews(sentByAccount.get(account.getId())),
                friendshipViews(receivedByAccount.get(account.getId())));
    }

    private static List<AccountRoleView> sortedRoles(Account account) {
        return account.getAccountRoles().stream()
                .sorted(Comparator.comparing(AccountRole::getRoleId))
                .map(AccountMapper::toAccountRoleView)
                .toList();
    }

    private static List<FriendshipView> friendshipViews(List<Friendship> friendships) {
        if (friendships == null) {
            return List.of();
        }
        return friendships.stream().map(AccountMapper::toFriendshipView).toList();
    }

    private AccountMapper() {
    }
}
