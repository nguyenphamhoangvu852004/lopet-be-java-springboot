package com.nguyenvu.lopet.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.nguyenvu.lopet.comment.CommentService;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.friendship.entity.Friendship;
import com.nguyenvu.lopet.friendship.entity.FriendshipStatus;
import com.nguyenvu.lopet.friendship.repository.FriendshipRepository;
import com.nguyenvu.lopet.post.dto.PostDtos;
import com.nguyenvu.lopet.post.entity.MediaType;
import com.nguyenvu.lopet.post.entity.PostScope;
import com.nguyenvu.lopet.post.repository.PostMediaRepository;
import com.nguyenvu.lopet.post.repository.PostRepository;
import com.nguyenvu.lopet.support.IntegrationTestBase;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Phân quyền phía ghi — tầng service")
class PostAuthorizationIntegrationTest extends IntegrationTestBase {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> IntegrationTestBase.jdbcUrl("lopet_java_authz_test"));
    }

    private static final String RUN = Long.toString(System.nanoTime(), 36);

    @Autowired
    private PostService postService;
    @Autowired
    private CommentService commentService;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private FriendshipRepository friendshipRepository;
    @Autowired
    private PostMediaRepository postMediaRepository;

    private Integer author;
    private Integer friend;
    private Integer stranger;

    @BeforeAll
    void seed() {
        inTransaction(() -> {
            Account authorAccount = account("authz-author");
            Account friendAccount = account("authz-friend");
            Account strangerAccount = account("authz-stranger");

            author = authorAccount.getId();
            friend = friendAccount.getId();
            stranger = strangerAccount.getId();

            friendshipRepository.save(Friendship.builder().sender(authorAccount).receiver(friendAccount)
                    .status(FriendshipStatus.ACCEPTED).build());
            friendshipRepository.save(Friendship.builder().sender(strangerAccount).receiver(authorAccount)
                    .status(FriendshipStatus.PENDING).build());
        });
    }

    private Account account(String name) {
        String unique = name + "-" + RUN;
        return accountRepository.save(Account.builder()
                .email(unique + "@authz.local").username(unique).password("x").isBanned(0).build());
    }

    private Integer personalPost(PostScope scope) {
        return postService.create(author, "personal-" + scope + "-" + System.nanoTime(),
                scope.name(), List.of()).postId();
    }

    private List<Integer> postWithMedia(int count) {
        List<PostService.UploadedMedia> medias = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            medias.add(new PostService.UploadedMedia("https://cdn.local/anh-" + i + "-" + System.nanoTime(),
                    MediaType.IMAGE));
        }
        PostDtos.CreatePostResponse created = postService.create(author,
                "bai-co-media-" + System.nanoTime(), PostScope.PUBLIC.name(), medias);

        List<Integer> ids = new ArrayList<>();
        ids.add(created.postId());
        created.postMedias().forEach(media -> ids.add(media.id()));
        return ids;
    }

    private Integer comment(Integer accountId, Integer postId, Integer replyTo) {
        return commentService.create(accountId, postId, replyTo, "xin chao", "").commentId();
    }

    private PostDtos.PostDetail readAs(Integer postId, Integer viewerId) {
        return postService.getOneById(postId, viewerId);
    }

    @Nested
    @DisplayName("Ràng buộc scope")
    class RangBuocScope {

        @Test
        @DisplayName("bài cá nhân nhận đủ PUBLIC / FRIEND / PRIVATE")
        void bai_ca_nhan_nhan_du_ba_scope() {
            for (PostScope scope : List.of(PostScope.PUBLIC, PostScope.FRIEND, PostScope.PRIVATE)) {
                assertThat(personalPost(scope)).isPositive();
            }
        }

        @Test
        @DisplayName("scope rác bị từ chối")
        void scope_rac_bi_tu_choi() {
            assertThatThrownBy(() -> postService.create(author, "x", "EVERYONE", List.of()))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    @Nested
    @DisplayName("Tác giả luôn là tài khoản gọi")
    class TacGiaLaTaiKhoan {

        @Test
        @DisplayName("bài mới mang account_id của người đăng")
        void bai_moi_mang_account_id() {
            Integer postId = personalPost(PostScope.PUBLIC);
            Integer accountId = inTransaction(() ->
                    postRepository.findByIdInternal(postId).orElseThrow().getAccount().getId());
            assertThat(accountId).isEqualTo(author);
        }

        @Test
        @DisplayName("tài khoản không tồn tại thì không đăng bài được")
        void tai_khoan_khong_ton_tai_khong_dang_duoc() {
            assertThatThrownBy(() -> postService.create(-1, "khong co tai khoan",
                    PostScope.PUBLIC.name(), List.of())).isInstanceOf(BadRequestException.class);
        }
    }

    @Nested
    @DisplayName("Sửa bài")
    class SuaBai {

        @Test
        @DisplayName("người không sở hữu bài không sửa được")
        void nguoi_la_khong_sua_duoc() {
            Integer postId = personalPost(PostScope.PUBLIC);
            assertThatThrownBy(() -> postService.update(postId, stranger, "cuop bai",
                    PostScope.PUBLIC.name(), List.of(), List.of()))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("tác giả sửa được bài của chính mình")
        void tac_gia_van_sua_duoc() {
            Integer postId = personalPost(PostScope.PUBLIC);
            assertThat(postService.update(postId, author, "sua lai", PostScope.PUBLIC.name(),
                    List.of(), List.of()).postId()).isEqualTo(postId);
        }

        @Test
        @DisplayName("không gửi oldIdsMedia thì media được giữ nguyên")
        void khong_nhac_toi_media_thi_giu_nguyen() {
            List<Integer> ids = postWithMedia(2);
            Integer postId = ids.get(0);

            PostDtos.UpdatePostResponse response = postService.update(postId, author, "chi sua caption",
                    PostScope.PUBLIC.name(), null, List.of());

            assertThat(response.postMedias()).hasSize(2);
            assertThat(postMediaRepository.findByPostId(postId)).hasSize(2);
        }

        @Test
        @DisplayName("gửi danh sách rỗng thì xoá hết media")
        void danh_sach_rong_thi_xoa_het() {
            List<Integer> ids = postWithMedia(2);
            Integer postId = ids.get(0);

            PostDtos.UpdatePostResponse response = postService.update(postId, author, "bo het anh",
                    PostScope.PUBLIC.name(), List.of(), List.of());

            assertThat(response.postMedias()).isEmpty();
            assertThat(postMediaRepository.findByPostId(postId)).isEmpty();
        }

        @Test
        @DisplayName("chỉ giữ đúng những id được liệt kê")
        void giu_dung_id_duoc_liet_ke() {
            List<Integer> ids = postWithMedia(3);
            Integer postId = ids.get(0);
            Integer giuLai = ids.get(1);

            PostDtos.UpdatePostResponse response = postService.update(postId, author, "bo bot anh",
                    PostScope.PUBLIC.name(), List.of(giuLai), List.of());

            assertThat(response.postMedias()).extracting(PostDtos.MediaWithId::id).containsExactly(giuLai);
            assertThat(postMediaRepository.findByPostId(postId)).extracting(media -> media.getId())
                    .containsExactly(giuLai);
        }

        @Test
        @DisplayName("id media của bài khác bị từ chối, media của bài này còn nguyên")
        void id_cua_bai_khac_bi_tu_choi() {
            List<Integer> cua_toi = postWithMedia(1);
            List<Integer> cua_bai_khac = postWithMedia(1);

            assertThatThrownBy(() -> postService.update(cua_toi.get(0), author, "muon anh bai khac",
                    PostScope.PUBLIC.name(), List.of(cua_bai_khac.get(1)), List.of()))
                    .isInstanceOf(BadRequestException.class);

            assertThat(postMediaRepository.findByPostId(cua_toi.get(0))).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Bình luận kế thừa quyền riêng tư của bài")
    class BinhLuan {

        @Test
        @DisplayName("bài PUBLIC cá nhân — ai cũng bình luận được")
        void bai_public_ai_cung_binh_luan_duoc() {
            assertThat(comment(stranger, personalPost(PostScope.PUBLIC), null)).isPositive();
        }

        @Test
        @DisplayName("bài FRIEND — bạn bè được, người lạ thì không")
        void bai_friend_chi_ban_be() {
            Integer postId = personalPost(PostScope.FRIEND);
            assertThat(comment(friend, postId, null)).isPositive();
            assertThatThrownBy(() -> comment(stranger, postId, null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("lời mời PENDING không được tính là bạn bè")
        void pending_khong_phai_ban_be() {
            Integer postId = personalPost(PostScope.FRIEND);
            assertThatThrownBy(() -> comment(stranger, postId, null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("bài PRIVATE — chỉ tác giả bình luận được")
        void bai_private_chi_tac_gia() {
            Integer postId = personalPost(PostScope.PRIVATE);
            assertThat(comment(author, postId, null)).isPositive();
            assertThatThrownBy(() -> comment(friend, postId, null)).isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> comment(stranger, postId, null)).isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("bình luận mang danh tính tài khoản, kèm hồ sơ của nó")
        void binh_luan_mang_danh_tinh_tai_khoan() {
            Integer postId = personalPost(PostScope.PUBLIC);
            comment(stranger, postId, null);

            var items = commentService.getAllFromPost(postId, stranger).comments();
            assertThat(items).hasSize(1);
            assertThat(items.getFirst().account().id()).isEqualTo(stranger);
            assertThat(items.getFirst().account().profile()).isNotNull();
        }

        @Test
        @DisplayName("không đọc được danh sách bình luận của bài mình không xem được")
        void khong_doc_duoc_binh_luan_bai_rieng_tu() {
            Integer postId = personalPost(PostScope.PRIVATE);
            comment(author, postId, null);

            assertThatThrownBy(() -> commentService.getAllFromPost(postId, stranger))
                    .isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> commentService.getAllFromPost(postId, null))
                    .isInstanceOf(BadRequestException.class);
            assertThat(commentService.getAllFromPost(postId, author).comments()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Trả lời bình luận phải giải quyết qua bài cha")
    class TraLoiBinhLuan {

        @Test
        @DisplayName("trả lời trong cùng một bài thì được")
        void tra_loi_cung_bai() {
            Integer postId = personalPost(PostScope.PUBLIC);
            Integer parent = comment(author, postId, null);
            assertThat(comment(stranger, postId, parent)).isPositive();
        }

        @Test
        @DisplayName("không ghép được comment cha của bài khác vào bài mình xem được")
        void khong_ghep_duoc_comment_bai_khac() {
            Integer privatePost = personalPost(PostScope.PRIVATE);
            Integer hiddenComment = comment(author, privatePost, null);
            Integer publicPost = personalPost(PostScope.PUBLIC);

            assertThatThrownBy(() -> comment(stranger, publicPost, hiddenComment))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("không trả lời được trực tiếp vào bài riêng tư")
        void khong_tra_loi_truc_tiep_bai_rieng_tu() {
            Integer privatePost = personalPost(PostScope.PRIVATE);
            Integer parent = comment(author, privatePost, null);
            assertThatThrownBy(() -> comment(stranger, privatePost, parent))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    @Nested
    @DisplayName("Thả tim kế thừa quyền riêng tư của bài")
    class ThaTim {

        private PostDtos.ReactResponse like(Integer postId, Integer accountId) {
            return postService.like(postId, accountId);
        }

        private PostDtos.ReactResponse unlike(Integer postId, Integer accountId) {
            return postService.unlike(postId, accountId);
        }

        @Test
        @DisplayName("bài PUBLIC — ai cũng thả tim được")
        void bai_public_ai_cung_tha_tim_duoc() {
            assertThat(like(personalPost(PostScope.PUBLIC), stranger).message())
                    .isEqualTo("Like post successfully");
        }

        @Test
        @DisplayName("bài FRIEND — bạn bè được, người lạ không")
        void bai_friend_chi_ban_be() {
            Integer postId = personalPost(PostScope.FRIEND);
            assertThat(like(postId, friend).message()).isEqualTo("Like post successfully");
            assertThatThrownBy(() -> like(postId, stranger)).isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("bài PRIVATE — chỉ tác giả")
        void bai_private_chi_tac_gia() {
            Integer postId = personalPost(PostScope.PRIVATE);
            assertThat(like(postId, author).message()).isEqualTo("Like post successfully");
            assertThatThrownBy(() -> like(postId, stranger)).isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("thả tim hai lần là idempotent, không phải lỗi")
        void tha_tim_hai_lan_idempotent() {
            Integer postId = personalPost(PostScope.PUBLIC);
            like(postId, friend);
            assertThat(like(postId, friend).message()).isEqualTo("You have already liked this post");
            unlike(postId, friend);
            assertThat(unlike(postId, friend).message())
                    .isEqualTo("You have already unliked this post");
        }
    }

    @Nested
    @DisplayName("Sửa/xoá không được dùng làm công cụ dò sự tồn tại")
    class KhongDoDuocSuTonTai {

        @Test
        @DisplayName("bài PRIVATE của người khác nạp ra rỗng (=> 404, không phải 403)")
        void bai_private_nap_ra_rong() {
            Integer postId = personalPost(PostScope.PRIVATE);

            assertThat(inTransaction(() ->
                    postRepository.findVisibleById(postId, stranger))).isEmpty();
            assertThat(inTransaction(() ->
                    postRepository.findVisibleById(postId, author))).isPresent();
        }

        @Test
        @DisplayName("bài PUBLIC của người khác vẫn nạp được (=> 403 đúng nghĩa)")
        void bai_public_van_nap_duoc() {
            Integer postId = personalPost(PostScope.PUBLIC);

            Integer ownerId = inTransaction(() ->
                    postRepository.findVisibleById(postId, stranger)
                            .orElseThrow().getAccount().getId());
            assertThat(ownerId).isEqualTo(author);
        }
    }

    @Nested
    @DisplayName("Chống đi vòng qua endpoint khác")
    class ChongDiVong {

        @Test
        @DisplayName("mọi luồng đọc đều che cùng một bài riêng tư")
        void moi_luong_doc_deu_che() {
            Integer postId = personalPost(PostScope.PRIVATE);
            assertThatThrownBy(() -> postService.getOneById(postId, stranger))
                    .isInstanceOf(NotFoundException.class);

            assertThat(postService.getByAccountId(author, stranger))
                    .noneMatch(post -> post.postId().equals(postId));

            assertThat(postService.getAll(null, stranger, 1, 100).content())
                    .noneMatch(post -> post.postId().equals(postId));

            assertThat(postService.getSuggestList(stranger))
                    .noneMatch(post -> post.postId().equals(postId));

            assertThatThrownBy(() -> commentService.getAllFromPost(postId, stranger))
                    .isInstanceOf(BadRequestException.class);
        }
    }
}
