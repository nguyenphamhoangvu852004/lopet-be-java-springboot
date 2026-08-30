package com.nguyenvu.lopet.accountprofile.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.nguyenvu.lopet.account.dto.AccountViews.AccountBrief;
import com.nguyenvu.lopet.accountprofile.entity.ProfileVisibility;

public final class AccountProfileDtos {

    public record ProfileSummary(
            Integer id,
            String fullName,
            String phoneNumber,
            String bio,
            String avatarUrl,
            String coverUrl,
            LocalDate dateOfBirth,
            String hometown,
            Integer sex,
            ProfileVisibility visibility) {
    }

    public record PublicProfile(
            Integer id,
            Integer accountId,
            String username,
            String fullName,
            String bio,
            String avatarUrl,
            String coverUrl) {
    }

    public record ProfileEntity(
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
            String coverUrl,
            ProfileVisibility visibility,
            AccountBrief account) {
    }

    private AccountProfileDtos() {
    }
}
