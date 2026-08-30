package com.nguyenvu.lopet.account.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public record GetAccountResponse(
        Integer id,
        String email,
        String username,
        List<String> roles,

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
