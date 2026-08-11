package com.nguyenvu.lopet.profile.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.nguyenvu.lopet.account.dto.AccountViews.AccountBrief;

public final class ProfileDtos {

    /** Chín trường — dùng cho GET /v1/profiles, GET /v1/profiles/accounts/:id và POST /v1/profiles */
    public record ProfileSummary(
            Integer id,
            String fullName,
            String phoneNumber,
            String bio,
            String avatarUrl,
            String coverUrl,
            LocalDate dateOfBirth,
            String hometown,
            Integer sex) {
    }

    /**
     * GET /v1/profiles/:id và PATCH /v1/profiles/:id trả về nguyên ENTITY (bản TS truyền thẳng
     * entity vào constructor DTO, nên mọi thuộc tính của entity — kể cả quan hệ {@code account} đã
     * nạp và ba cột audit — đều lọt ra ngoài).
     */
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
            AccountBrief account) {
    }

    /** setToAccount chỉ gán sáu trường — ba trường còn lại không xuất hiện trong JSON */
    public record ProfileShort(
            Integer id,
            String fullName,
            String phoneNumber,
            String bio,
            String avatarUrl,
            String coverUrl) {
    }

    public record CreateProfileRequest(
            String fullName,
            String phoneNumber,
            String bio,
            String dateOfBirth,
            String hometown,
            Integer sex) {
    }

    public record UpdateProfileRequest(
            String fullName,
            String phoneNumber,
            String bio,
            String dateOfBirth,
            String hometown,
            Integer sex) {
    }

    private ProfileDtos() {
    }
}
