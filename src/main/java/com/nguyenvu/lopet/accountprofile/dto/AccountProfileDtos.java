package com.nguyenvu.lopet.accountprofile.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.nguyenvu.lopet.account.dto.AccountViews.AccountBrief;
import com.nguyenvu.lopet.accountprofile.entity.ProfileVisibility;

public final class AccountProfileDtos {

    /** Hồ sơ của CHÍNH MÌNH — {@code GET /v1/account-profiles/me} */
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

    /**
     * Hồ sơ của NGƯỜI KHÁC — {@code GET /v1/account-profiles/accounts/{id}}.
     *
     * <p>Cố ý hẹp hơn {@link ProfileSummary}: không có {@code phoneNumber}, {@code dateOfBirth},
     * {@code hometown}. Ba trường đó là dữ liệu liên lạc và nhận dạng, và việc một người mở hồ sơ ở
     * mức PUBLIC không có nghĩa họ muốn phát số điện thoại của mình ra cho cả internet. Cũng không có
     * {@code visibility}: cấu hình riêng tư của một người không phải việc của người xem.
     */
    public record PublicProfile(
            Integer id,
            Integer accountId,
            String username,
            String fullName,
            String bio,
            String avatarUrl,
            String coverUrl) {
    }

    /**
     * PUT /v1/account-profiles trả về nguyên ENTITY (bản TS truyền thẳng entity vào
     * constructor DTO, nên mọi thuộc tính của entity — kể cả quan hệ {@code account} đã nạp và ba
     * cột audit — đều lọt ra ngoài).
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
            ProfileVisibility visibility,
            AccountBrief account) {
    }

    private AccountProfileDtos() {
    }
}
