package com.nguyenvu.lopet.post.repository;

public final class PostVisibility {

    public static final String VISIBLE_TO = """
            (
              p.postScope = com.nguyenvu.lopet.post.entity.PostScope.PUBLIC
              or (:viewerId is not null and (
                   p.account.id = :viewerId
                   or (p.postScope = com.nguyenvu.lopet.post.entity.PostScope.FRIEND
                       and exists (
                            select 1 from Friendship f
                            where f.status = com.nguyenvu.lopet.friendship.entity.FriendshipStatus.ACCEPTED
                              and ((f.sender.id = :viewerId and f.receiver.id = p.account.id)
                                or (f.receiver.id = :viewerId and f.sender.id = p.account.id))))
              ))
            )
            """;

    private PostVisibility() {
    }
}
