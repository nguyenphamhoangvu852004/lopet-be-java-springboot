package com.nguyenvu.lopet.account.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    public record AccountListItem(
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime deletedAt,
            Integer id,
            String email,
            String username,
            Integer isBanned,
            AccountProfileView profile) {
    }

    public record AccountDetail(
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime deletedAt,
            Integer id,
            String email,
            String username,
            Integer isBanned,
            AccountProfileView profile) {
    }

    private AccountViews() {
    }
}
