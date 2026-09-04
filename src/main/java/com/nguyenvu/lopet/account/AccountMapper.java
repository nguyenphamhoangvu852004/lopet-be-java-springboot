package com.nguyenvu.lopet.account;

import com.nguyenvu.lopet.account.dto.AccountViews;
import com.nguyenvu.lopet.account.dto.AccountViews.AccountBrief;
import com.nguyenvu.lopet.account.dto.AccountViews.AccountProfileView;
import com.nguyenvu.lopet.account.dto.GetAccountResponse;
import com.nguyenvu.lopet.account.dto.GetAccountResponse.AccountProfileSummary;
import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.accountprofile.entity.AccountProfile;

public final class AccountMapper {

    public static GetAccountResponse toGetAccountResponse(Account account) {
        AccountProfile profile = account.getAccountProfile();
        AccountProfileSummary summary = profile == null ? null : new AccountProfileSummary(
                profile.getId(),
                profile.getFullName(),
                profile.getBio(),
                profile.getPhoneNumber(),
                profile.getAvatarUrl(),
                profile.getCoverUrl());

        return new GetAccountResponse(account.getId(), account.getEmail(), account.getUsername(), summary);
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

    public static AccountViews.AccountDetail toDetail(Account account) {
        return new AccountViews.AccountDetail(account.getCreatedAt(), account.getUpdatedAt(),
                account.getDeletedAt(), account.getId(), account.getEmail(), account.getUsername(),
                account.getIsBanned(), toAccountProfileView(account.getAccountProfile()));
    }

    public static AccountViews.AccountListItem toListItem(Account account) {
        return new AccountViews.AccountListItem(account.getCreatedAt(), account.getUpdatedAt(),
                account.getDeletedAt(), account.getId(), account.getEmail(), account.getUsername(),
                account.getIsBanned(), toAccountProfileView(account.getAccountProfile()));
    }

    private AccountMapper() {
    }
}
