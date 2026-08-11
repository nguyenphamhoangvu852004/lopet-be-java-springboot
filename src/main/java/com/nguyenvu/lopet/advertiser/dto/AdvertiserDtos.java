package com.nguyenvu.lopet.advertiser.dto;

import java.time.LocalDateTime;

import com.nguyenvu.lopet.advertiser.entity.AdvertiserStatus;

public final class AdvertiserDtos {

    public record RegisterRequest(String companyName) {
    }

    public record ReviewRequest(String status) {
    }

    /**
     * {@code balance} và {@code dailyLimit} là {@code Double} chứ không phải {@code BigDecimal}:
     * bản TS có transformer ép decimal của driver mysql về number, nên JSON ra {@code 0} chứ không
     * phải {@code "0.00"}.
     */
    public record AdvertiserProfileResponse(
            Integer id,
            Integer accountId,
            String username,
            String companyName,
            AdvertiserStatus status,
            Double balance,
            Double dailyLimit,
            Integer approvedById,
            LocalDateTime approvedAt,
            LocalDateTime createdAt) {
    }

    private AdvertiserDtos() {
    }
}
