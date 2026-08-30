package com.nguyenvu.lopet.post;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.post.entity.Post;
import com.nguyenvu.lopet.post.entity.PostLike;
import com.nguyenvu.lopet.post.entity.PostScope;

@DisplayName("PostMapper — bài không có tác giả")
class PostMapperNullAuthorTest {

    private static Post postWithoutAuthor() {
        return Post.builder()
                .id(1)
                .account(null)
                .content("bai cu khong con tac gia")
                .postScope(PostScope.PUBLIC)
                .build();
    }

    @Test
    @DisplayName("toListItem trả accountId null thay vì ném")
    void list_item_khong_nem() {
        var item = PostMapper.toListItem(postWithoutAuthor());

        assertThat(item.accountId()).isNull();
        assertThat(item.content()).isEqualTo("bai cu khong con tac gia");
    }

    @Test
    @DisplayName("toSuggestItem và toDetail cũng vậy")
    void suggest_va_detail_khong_nem() {
        assertThat(PostMapper.toSuggestItem(postWithoutAuthor()).accountId()).isNull();
        assertThat(PostMapper.toDetail(postWithoutAuthor()).accountId()).isNull();
    }

    @Test
    @DisplayName("lượt thích mất tài khoản bị bỏ qua, nhưng vẫn được đếm")
    void luot_thich_mat_tai_khoan() {
        Post post = postWithoutAuthor();
        post.getPostLikes().add(PostLike.builder().id(1).post(post).account(null).build());
        post.getPostLikes().add(PostLike.builder().id(2).post(post)
                .account(Account.builder().id(7).username("vu").email("vu@test.local").build())
                .build());

        var item = PostMapper.toListItem(post);

        assertThat(item.likeAmount()).isEqualTo(2);
        assertThat(item.likeList()).hasSize(1);
        assertThat(item.likeList().getFirst().id()).isEqualTo(7);
    }
}
