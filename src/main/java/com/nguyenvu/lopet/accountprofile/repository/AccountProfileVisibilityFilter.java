package com.nguyenvu.lopet.accountprofile.repository;

public final class AccountProfileVisibilityFilter {

    public static final String VISIBLE_TO = """
            (
              p.visibility = com.nguyenvu.lopet.accountprofile.entity.ProfileVisibility.PUBLIC
              or (:viewerId is not null and (
                   a.id = :viewerId
                   or (p.visibility = com.nguyenvu.lopet.accountprofile.entity.ProfileVisibility.FRIEND
                       and exists (
                            select 1 from Friendship f
                            where f.status = com.nguyenvu.lopet.friendship.entity.FriendshipStatus.ACCEPTED
                              and ((f.sender.id = :viewerId and f.receiver.id = a.id)
                                or (f.receiver.id = :viewerId and f.sender.id = a.id))))
              ))
            )
            """;

    private AccountProfileVisibilityFilter() {
    }
}
