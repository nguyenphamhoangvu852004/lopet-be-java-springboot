package com.nguyenvu.lopet.post.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.ForbiddenException;

@DisplayName("Post — các bất biến của aggregate")
class PostTest {

    private static final Integer OWNER_ID = 7;
    private static final Integer STRANGER_ID = 99;

    private static Account owner() {
        return Account.builder().id(OWNER_ID).username("vu").email("vu@test.local").build();
    }

    private static Post aPost() {
        return Post.create(owner(), "nội dung gốc");
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("không có tác giả thì không tạo được bài")
        void author_is_required() {
            assertThatThrownBy(() -> Post.create(null, "gì đó"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("A post must have an author");
        }

        @Test
        @DisplayName("content null thành chuỗi rỗng, content thừa khoảng trắng thì được trim")
        void content_is_normalised() {
            assertThat(Post.create(owner(), null).getContent()).isEmpty();
            assertThat(Post.create(owner(), "  xin chào  ").getContent()).isEqualTo("xin chào");
        }

        @Test
        @DisplayName("bài mới có collection rỗng chứ không phải null")
        void collections_are_initialised() {
            Post post = aPost();

            assertThat(post.getPostMedias()).isEmpty();
            assertThat(post.getPostLikes()).isEmpty();
            assertThat(post.likeCount()).isZero();
        }
    }

    @Nested
    @DisplayName("quyền sở hữu")
    class Ownership {

        @Test
        @DisplayName("người lạ không sửa được nội dung")
        void stranger_cannot_edit() {
            Post post = aPost();

            assertThatThrownBy(() -> post.editContentBy(STRANGER_ID, "cướp bài"))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("You are not the owner of this post");
            assertThat(post.getContent()).isEqualTo("nội dung gốc");
        }

        @Test
        @DisplayName("chủ bài sửa được")
        void owner_can_edit() {
            Post post = aPost();

            post.editContentBy(OWNER_ID, "  nội dung mới  ");

            assertThat(post.getContent()).isEqualTo("nội dung mới");
        }

        @Test
        @DisplayName("bài mất tác giả thì không ai là chủ")
        void authorless_post_has_no_owner() {
            Post post = PostFixtures.authorless(1, "bài mồ côi");

            assertThat(post.authorId()).isNull();
            assertThat(post.isOwnedBy(OWNER_ID)).isFalse();
            assertThat(post.isOwnedBy(null)).isFalse();
        }

        @Test
        @DisplayName("người lạ không xoá được bài")
        void stranger_cannot_delete() {
            Post post = aPost();

            assertThatThrownBy(() -> post.deleteBy(STRANGER_ID)).isInstanceOf(ForbiddenException.class);
            assertThat(post.isDeleted()).isFalse();
        }
    }

    @Nested
    @DisplayName("media")
    class Media {

        @Test
        @DisplayName("addMedia nối cả hai chiều và xuất hiện trong mediasSorted")
        void add_media_wires_both_sides() {
            Post post = aPost();

            PostMedia media = post.addMedia("https://cdn.test.local/a.png", MediaType.IMAGE);

            assertThat(media.getPost()).isSameAs(post);
            assertThat(media.getMediaType()).isEqualTo(MediaType.IMAGE);
            assertThat(post.mediasSorted()).containsExactly(media);
        }

        @Test
        @DisplayName("url rỗng hoặc thiếu loại media thì bị từ chối")
        void add_media_validates_its_input() {
            Post post = aPost();

            assertThatThrownBy(() -> post.addMedia("  ", MediaType.IMAGE))
                    .isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> post.addMedia("https://cdn.test.local/a.png", null))
                    .isInstanceOf(BadRequestException.class);
            assertThat(post.getPostMedias()).isEmpty();
        }

        @Test
        @DisplayName("mediasSorted sắp theo id, media chưa lưu xếp cuối")
        void medias_are_sorted_by_id() {
            Post post = PostFixtures.withMedia(1, owner(), "bài", 5, 2);
            post.addMedia("https://cdn.test.local/new.png", MediaType.IMAGE);

            List<Integer> ids = post.mediasSorted().stream().map(PostMedia::getId).toList();

            assertThat(ids).containsExactly(2, 5, null);
        }

        @Test
        @DisplayName("keepOnlyMedia(null) giữ nguyên tất cả")
        void keep_null_keeps_everything() {
            Post post = PostFixtures.withMedia(1, owner(), "bài", 1, 2, 3);

            post.keepOnlyMedia(null);

            assertThat(post.getPostMedias()).hasSize(3);
        }

        @Test
        @DisplayName("keepOnlyMedia bỏ những media không nằm trong danh sách giữ")
        void keep_drops_the_rest() {
            Post post = PostFixtures.withMedia(1, owner(), "bài", 1, 2, 3);

            post.keepOnlyMedia(List.of(2));

            assertThat(post.mediasSorted()).extracting(PostMedia::getId).containsExactly(2);
        }

        @Test
        @DisplayName("keepOnlyMedia(rỗng) dọn sạch media")
        void keep_empty_clears_everything() {
            Post post = PostFixtures.withMedia(1, owner(), "bài", 1, 2);

            post.keepOnlyMedia(List.of());

            assertThat(post.getPostMedias()).isEmpty();
        }

        @Test
        @DisplayName("giữ id của bài khác thì báo lỗi và không đụng vào media hiện có")
        void keep_rejects_a_foreign_id() {
            Post post = PostFixtures.withMedia(1, owner(), "bài", 1, 2);

            assertThatThrownBy(() -> post.keepOnlyMedia(List.of(1, 42)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Old media not found: ID 42");
            assertThat(post.getPostMedias()).hasSize(2);
        }

        @Test
        @DisplayName("media vừa thêm chưa có id thì keepOnlyMedia không xoá nhầm")
        void keep_never_drops_unsaved_media() {
            Post post = PostFixtures.withMedia(1, owner(), "bài", 1);
            post.addMedia("https://cdn.test.local/new.png", MediaType.IMAGE);

            post.keepOnlyMedia(List.of());

            assertThat(post.getPostMedias()).hasSize(1);
            assertThat(post.mediasSorted().getFirst().getId()).isNull();
        }

        @Test
        @DisplayName("getPostMedias / getPostLikes trả collection không sửa được từ bên ngoài")
        void collections_are_read_only() {
            Post post = PostFixtures.withMedia(1, owner(), "bài", 1);

            assertThatThrownBy(() -> post.getPostMedias().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> post.getPostLikes().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("xoá mềm")
    class SoftDelete {

        @Test
        @DisplayName("deleteBy đánh dấu cả bài lẫn từng media")
        void delete_cascades_to_media() {
            Post post = PostFixtures.withMedia(1, owner(), "bài", 1, 2);

            post.deleteBy(OWNER_ID);

            assertThat(post.isDeleted()).isTrue();
            assertThat(post.getDeletedAt()).isNotNull();
            assertThat(post.getPostMedias()).allMatch(PostMedia::isDeleted);
        }

        @Test
        @DisplayName("xoá lần hai không ghi đè mốc thời gian xoá gốc")
        void delete_is_idempotent() {
            Post post = aPost();
            post.deleteBy(OWNER_ID);
            var firstDeletedAt = post.getDeletedAt();

            post.deleteBy(OWNER_ID);

            assertThat(post.getDeletedAt()).isEqualTo(firstDeletedAt);
        }
    }

    @Nested
    @DisplayName("requirePublishable")
    class Publishable {

        @Test
        @DisplayName("không nội dung và không media thì không đăng được")
        void empty_post_is_rejected() {
            Post post = Post.create(owner(), "   ");

            assertThatThrownBy(post::requirePublishable)
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("A post must have content or at least one media");
        }

        @Test
        @DisplayName("bài chỉ có ảnh, không chữ vẫn hợp lệ")
        void media_only_post_is_allowed() {
            Post post = Post.create(owner(), null);
            post.addMedia("https://cdn.test.local/a.png", MediaType.IMAGE);

            post.requirePublishable();

            assertThat(post.getContent()).isEmpty();
        }

        @Test
        @DisplayName("bài chỉ có chữ vẫn hợp lệ")
        void text_only_post_is_allowed() {
            aPost().requirePublishable();
        }
    }
}
