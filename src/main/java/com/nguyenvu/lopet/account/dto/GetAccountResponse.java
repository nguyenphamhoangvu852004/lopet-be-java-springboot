package com.nguyenvu.lopet.account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public record GetAccountResponse(
        Integer id,
        String email,
        String username,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        AccountProfileSummary profile) {

    public record AccountProfileSummary(
            Integer id,
            String fullName,
            String bio,
            String phoneNumber,
            String avatarUrl,
            String coverUrl) {
    }
}
