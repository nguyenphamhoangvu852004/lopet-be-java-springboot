package com.nguyenvu.lopet.post;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.post.entity.Post;
import com.nguyenvu.lopet.post.entity.PostFixtures;

@DisplayName("PostMapper — post without an author")
class PostMapperNullAuthorTest {

    private static final String CONTENT = "an old post that no longer has an author";

    private static Post postWithoutAuthor() {
        return PostFixtures.authorless(1, CONTENT);
    }

    @Test
    @DisplayName("toListItem returns a null accountId instead of throwing")
    void list_item_does_not_throw() {
        var item = PostMapper.toListItem(postWithoutAuthor());

        assertThat(item.accountId()).isNull();
        assertThat(item.content()).isEqualTo(CONTENT);
    }

    @Test
    @DisplayName("toSuggestItem and toDetail behave the same")
    void suggest_and_detail_do_not_throw() {
        assertThat(PostMapper.toSuggestItem(postWithoutAuthor()).accountId()).isNull();
        assertThat(PostMapper.toDetail(postWithoutAuthor()).accountId()).isNull();
    }

    @Test
    @DisplayName("a like whose account is gone is skipped, but still counted")
    void like_with_a_missing_account() {
        Post post = PostFixtures.authorless(1, CONTENT,
                PostFixtures.like(1, null),
                PostFixtures.like(2, Account.builder().id(7).username("vu").email("vu@test.local").build()));

        var item = PostMapper.toListItem(post);

        assertThat(item.likeAmount()).isEqualTo(2);
        assertThat(item.likeList()).hasSize(1);
        assertThat(item.likeList().getFirst().id()).isEqualTo(7);
    }
}
