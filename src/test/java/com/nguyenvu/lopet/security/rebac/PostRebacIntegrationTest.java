package com.nguyenvu.lopet.security.rebac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.comment.entity.Comment;
import com.nguyenvu.lopet.comment.repository.CommentRepository;
import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.post.entity.Post;
import com.nguyenvu.lopet.post.entity.PostScope;
import com.nguyenvu.lopet.post.repository.PostRepository;
import com.nguyenvu.lopet.support.AuthTestSupport;
import com.nguyenvu.lopet.support.IntegrationTestBase;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("ReBAC trên post và comment — engine + resolver + database thật")
class PostRebacIntegrationTest extends IntegrationTestBase {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> IntegrationTestBase.jdbcUrl("lopet_java_rebac_test"));
    }

    private static final String RUN = Long.toString(System.nanoTime(), 36);

    @Autowired
    private RebacEngine rebac;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private CommentRepository commentRepository;

    private Integer authorId;
    private Integer strangerId;
    private Integer moderatorId;

    private Integer publicPost;
    private Integer privatePost;

    private Integer commentByAuthor;
    private Integer commentOnPrivatePost;

    @BeforeAll
    void seed() {
        inTransaction(() -> {
            commentRepository.deleteAll();
            postRepository.deleteAll();
        });

        inTransaction(() -> {
            Account author = account("rebac-tacgia");
            Account stranger = account("rebac-nguoila");
            Account moderator = account("rebac-kiemduyet");

            authorId = author.getId();
            strangerId = stranger.getId();
            moderatorId = moderator.getId();

            publicPost = post(author, "rebac-public", PostScope.PUBLIC);
            privatePost = post(author, "rebac-private", PostScope.PRIVATE);

            commentByAuthor = comment(author, publicPost, "binh luan cua tac gia");
            commentOnPrivatePost = comment(author, privatePost, "binh luan trong bai rieng tu");
        });
    }

    @AfterEach
    void clearIdentity() {
        AuthTestSupport.clear();
    }

    @Nested
    @DisplayName("Xoá bài")
    class XoaBai {

        @Test
        @DisplayName("MODERATOR xoá được bài PRIVATE của người khác — bug cũ, giờ phải chạy")
        void moderator_xoa_duoc_bai_private_cua_nguoi_khac() {
            AuthTestSupport.actAs(moderatorId, List.of("MODERATOR"));

            assertThatCode(() -> rebac.require(RebacModel.POST_DELETE, ObjectRef.post(privatePost)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("ADMIN cũng xoá được")
        void admin_xoa_duoc() {
            AuthTestSupport.actAs(moderatorId, List.of("ADMIN"));

            assertThatCode(() -> rebac.require(RebacModel.POST_DELETE, ObjectRef.post(privatePost)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("chủ bài xoá được bài của mình")
        void chu_bai_xoa_duoc_bai_minh() {
            AuthTestSupport.actAs(authorId);

            assertThatCode(() -> rebac.require(RebacModel.POST_DELETE, ObjectRef.post(privatePost)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("người lạ xoá bài PRIVATE nhận 404 chứ không phải 403 — chống dò id")
        void nguoi_la_xoa_bai_private_nhan_404() {
            AuthTestSupport.actAs(strangerId);

            assertThatThrownBy(() -> rebac.require(RebacModel.POST_DELETE, ObjectRef.post(privatePost)))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("người lạ xoá bài PUBLIC nhận 403 — nạp được nhưng không phải chủ")
        void nguoi_la_xoa_bai_public_nhan_403() {
            AuthTestSupport.actAs(strangerId);

            assertThatThrownBy(() -> rebac.require(RebacModel.POST_DELETE, ObjectRef.post(publicPost)))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Bạn không sở hữu tài nguyên này");
        }

        @Test
        @DisplayName("khách chưa đăng nhập nhận 403 'Chưa xác thực', không phải 401")
        void khach_nhan_403_chua_xac_thuc() {
            AuthTestSupport.clear();

            assertThatThrownBy(() -> rebac.require(RebacModel.POST_DELETE, ObjectRef.post(publicPost)))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Chưa xác thực");
        }

        @Test
        @DisplayName("bài không tồn tại cũng là 404")
        void bai_khong_ton_tai_la_404() {
            AuthTestSupport.actAs(authorId);

            assertThatThrownBy(() -> rebac.require(RebacModel.POST_DELETE, ObjectRef.post(999_999)))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Sửa bài")
    class SuaBai {

        @Test
        @DisplayName("MODERATOR KHÔNG sửa được bài người khác: kiểm duyệt thì xoá, không viết hộ")
        void moderator_khong_sua_duoc_bai_nguoi_khac() {
            AuthTestSupport.actAs(moderatorId, List.of("MODERATOR"));

            assertThatThrownBy(() -> rebac.require(RebacModel.POST_UPDATE, ObjectRef.post(publicPost)))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Bạn không sở hữu tài nguyên này");
        }

        @Test
        @DisplayName("ADMIN cũng không sửa được")
        void admin_cung_khong_sua_duoc() {
            AuthTestSupport.actAs(moderatorId, List.of("ADMIN"));

            assertThatThrownBy(() -> rebac.require(RebacModel.POST_UPDATE, ObjectRef.post(publicPost)))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("chủ bài sửa được")
        void chu_bai_sua_duoc() {
            AuthTestSupport.actAs(authorId);

            assertThatCode(() -> rebac.require(RebacModel.POST_UPDATE, ObjectRef.post(publicPost)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Quan hệ suy ra từ bảng domain")
    class QuanHeSuyRa {

        @Test
        @DisplayName("người lạ không thấy bài PRIVATE — resolver trả rỗng, thành 404")
        void nguoi_la_khong_thay_bai_private() {
            assertThat(rebac.relationsOf(ObjectRef.post(privatePost), principal(strangerId))).isEmpty();
        }

        @Test
        @DisplayName("tác giả là VIEWER của chính bài PRIVATE của mình")
        void tac_gia_van_xem_duoc_bai_private_cua_minh() {
            assertThat(rebac.relationsOf(ObjectRef.post(privatePost), principal(authorId)))
                    .hasValueSatisfying(relations -> assertThat(relations)
                            .containsExactlyInAnyOrder(Relation.VIEWER, Relation.OWNER));
        }

        @Test
        @DisplayName("chủ bài có cả VIEWER lẫn OWNER")
        void chu_bai_co_ca_hai_quan_he() {
            assertThat(rebac.relationsOf(ObjectRef.post(publicPost), principal(authorId)))
                    .hasValueSatisfying(relations ->
                            assertThat(relations).containsExactlyInAnyOrder(Relation.VIEWER, Relation.OWNER));
        }

        @Test
        @DisplayName("khách vẫn là VIEWER của bài công khai")
        void khach_van_xem_duoc_bai_cong_khai() {
            assertThat(rebac.relationsOf(ObjectRef.post(publicPost), null))
                    .hasValueSatisfying(relations ->
                            assertThat(relations).containsExactly(Relation.VIEWER));
        }

        @Test
        @DisplayName("MODERATOR có quan hệ MODERATOR với platform, và luôn có AUTHENTICATED")
        void quan_he_nen_tang_suy_ra_tu_token() {
            assertThat(rebac.relationsOf(ObjectRef.platform(),
                    new com.nguyenvu.lopet.security.jwt.UserPrincipal(moderatorId, "m@test.local",
                            List.of("MODERATOR"))))
                    .hasValueSatisfying(relations -> assertThat(relations)
                            .containsExactlyInAnyOrder(Relation.AUTHENTICATED, Relation.MODERATOR));
        }

        @Test
        @DisplayName("khách không có quan hệ nào với platform, kể cả AUTHENTICATED")
        void khach_khong_co_quan_he_nen_tang() {
            assertThat(rebac.relationsOf(ObjectRef.platform(), null))
                    .hasValueSatisfying(relations -> assertThat(relations).isEmpty());
        }
    }

    @Nested
    @DisplayName("Xoá bình luận — quyền giải quyết qua bài cha")
    class XoaBinhLuan {

        @Test
        @DisplayName("chủ bình luận xoá được")
        void chu_binh_luan_xoa_duoc() {
            AuthTestSupport.actAs(authorId);

            assertThatCode(() -> rebac.require(RebacModel.COMMENT_DELETE, ObjectRef.comment(commentByAuthor)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("MODERATOR xoá được bình luận trong bài PRIVATE mà chính họ không xem được")
        void moderator_xoa_duoc_binh_luan_trong_bai_private() {
            AuthTestSupport.actAs(moderatorId, List.of("MODERATOR"));

            assertThatCode(() -> rebac.require(RebacModel.COMMENT_DELETE,
                    ObjectRef.comment(commentOnPrivatePost))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("người ngoài nhận 404 với bình luận trong bài PRIVATE, không phải 403")
        void nguoi_ngoai_nhan_404_voi_binh_luan_trong_bai_private() {
            AuthTestSupport.actAs(strangerId);

            assertThatThrownBy(() -> rebac.require(RebacModel.COMMENT_DELETE,
                    ObjectRef.comment(commentOnPrivatePost))).isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("người ngoài nhận 403 với bình luận trong bài PUBLIC — thấy được, không phải chủ")
        void nguoi_ngoai_nhan_403_voi_binh_luan_trong_bai_public() {
            AuthTestSupport.actAs(strangerId);

            assertThatThrownBy(() -> rebac.require(RebacModel.COMMENT_DELETE,
                    ObjectRef.comment(commentByAuthor)))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Bạn không sở hữu tài nguyên này");
        }
    }

    private com.nguyenvu.lopet.security.jwt.UserPrincipal principal(Integer accountId) {
        return new com.nguyenvu.lopet.security.jwt.UserPrincipal(
                accountId, "p-" + accountId + "@test.local", List.of());
    }

    private Account account(String name) {
        String unique = name + "-" + RUN;
        return accountRepository.save(Account.builder()
                .email(unique + "@test.local").username(unique).password("x").isBanned(0).build());
    }

    private Integer post(Account owner, String content, PostScope scope) {
        return postRepository.save(Post.builder()
                .account(owner)
                .content(content + "-" + RUN)
                .postScope(scope)
                .build()).getId();
    }

    private Integer comment(Account owner, Integer postId, String text) {
        return commentRepository.save(Comment.builder()
                .account(owner)
                .post(postRepository.findById(postId).orElseThrow())
                .text(text)
                .build()).getId();
    }
}
