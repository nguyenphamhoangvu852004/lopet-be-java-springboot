package com.nguyenvu.lopet.post;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.post.entity.Post;
import com.nguyenvu.lopet.post.entity.PostLike;
import com.nguyenvu.lopet.post.entity.PostScope;
import com.nguyenvu.lopet.post.entity.PostType;

/**
 * Bài viết KHÔNG có tác giả phải map được, không được ném.
 *
 * <p>{@code posts.account_id} là cột NULLABLE — dữ liệu cũ có thể còn hàng không quy được về tài
 * khoản nào, và script di trú cố ý GIỮ LẠI những bài đó thay vì xoá nội dung người dùng đã viết
 * (xem {@code scripts/revert-pet-to-account-migration.sql} bước 5).
 *
 * <p>Bộ test này tồn tại vì một lỗi thật: mapper gọi thẳng {@code post.getAccount().getId()} và cả
 * trang feed vỡ bằng NullPointerException ngay khi có MỘT hàng như vậy. Mapper chạy trên đường ĐỌC,
 * nên một hàng dữ liệu cũ không được phép làm hỏng phản hồi của mọi người.
 */
@DisplayName("PostMapper — bài không có tác giả")
class PostMapperNullAuthorTest {

    private static Post postWithoutAuthor() {
        return Post.builder()
                .id(1)
                .account(null)
                .content("bai cu khong con tac gia")
                .postType(PostType.USER)
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

    /**
     * Lượt thích mất tài khoản bị BỎ QUA khỏi danh sách, nhưng {@code likeAmount} vẫn đếm đủ: số
     * đếm lấy từ {@code postLikes.size()}, còn danh sách chỉ dùng để hiện AI đã thích — một dòng
     * không có danh tính thì không hiện được gì.
     */
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
