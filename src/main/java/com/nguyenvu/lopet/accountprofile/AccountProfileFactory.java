package com.nguyenvu.lopet.accountprofile;

import com.nguyenvu.lopet.accountprofile.entity.AccountProfile;

public final class AccountProfileFactory {

    public static AccountProfile seedFor(String username) {
        return AccountProfile.builder()
                .fullName(username)
                .bio("Hello Lopet, I am " + username)
                .phoneNumber("")
                .hometown("")
                .avatarUrl("")
                .coverUrl("")
                .sex(null)
                .dateOfBirth(null)
                .build();
    }

    private AccountProfileFactory() {
    }
}
