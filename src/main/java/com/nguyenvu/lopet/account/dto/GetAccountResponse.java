package com.nguyenvu.lopet.account.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO của GET /v1/accounts/:id. Không có trường password — DTO này đi thẳng ra response.
 *
 * <p>{@code profile} được bỏ hẳn khỏi JSON khi tài khoản chưa có hồ sơ: bản TS thoát sớm trước khi
 * gán khoá đó, nên client cũ không bao giờ thấy {@code "profile": null}.
 */
public record GetAccountResponse(
        Integer id,
        String email,
        String username,
        List<String> roles,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        AccountProfileSummary profile) {

    /** Đúng sáu trường mà AccountServiceImpl gán — các trường khác của profile không xuất hiện */
    public record AccountProfileSummary(
            Integer id,
            String fullName,
            String bio,
            String phoneNumber,
            String avatarUrl,
            String coverUrl) {
    }
}
