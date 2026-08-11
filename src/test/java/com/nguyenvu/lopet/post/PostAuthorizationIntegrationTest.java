package com.nguyenvu.lopet.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

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
import com.nguyenvu.lopet.group.entity.Group;
import com.nguyenvu.lopet.group.entity.GroupMember;
import com.nguyenvu.lopet.group.entity.GroupMemberRole;
import com.nguyenvu.lopet.group.entity.GroupType;
import com.nguyenvu.lopet.group.repository.GroupMemberRepository;
import com.nguyenvu.lopet.group.repository.GroupRepository;
import com.nguyenvu.lopet.post.dto.PostDtos;
import com.nguyenvu.lopet.post.entity.PostScope;
import com.nguyenvu.lopet.post.repository.PostRepository;
import com.nguyenvu.lopet.support.IntegrationTestBase;

/**
 * Bản port của {@code postAuthorization.integration.test.ts} — phía GHI của ma trận phân quyền:
 * tạo bài, bình luận, trả lời, thả tim, sửa scope.
 *
 * <p>{@link PostVisibilityIntegrationTest} đã phủ phía ĐỌC ở tầng query. File này chạy ở tầng
 * SERVICE vì các quy tắc còn lại — "ai được đăng vào group nào", "comment cha có thuộc đúng bài
 * không" — không nằm trong câu truy vấn mà nằm ở PostService/CommentService.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Phân quyền phía ghi — tầng service")
class PostAuthorizationIntegrationTest extends IntegrationTestBase {

    /**
     * Database RIÊNG cho lớp test này. Hai bộ test tích hợp cùng ghi vào một database sẽ nhìn thấy
     * bài viết của nhau, làm mọi khẳng định "người xem thấy ĐÚNG những bài này" mất nghĩa — bản TS
     * cũng tách database theo từng file vì lý do đó.
     */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> IntegrationTestBase.jdbcUrl("lopet_java_authz_test"));
    }

    @Autowired
    private PostService postService;
    @Autowired
    private CommentService commentService;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private GroupMemberRepository groupMemberRepository;
    @Autowired
    private FriendshipRepository friendshipRepository;

    private Integer author;
    private Integer friend;
    private Integer stranger;
    private Integer groupMember;
    private Integer publicGroup;
    private Integer privateGroup;

    @BeforeAll
    void seed() {
        inTransaction(() -> {
            Account authorAccount = account("authz-author");
            Account friendAccount = account("authz-friend");
            Account strangerAccount = account("authz-stranger");
            Account memberAccount = account("authz-groupmember");

            author = authorAccount.getId();
            friend = friendAccount.getId();
            stranger = strangerAccount.getId();
            groupMember = memberAccount.getId();

            friendshipRepository.save(Friendship.builder().sender(authorAccount).receiver(friendAccount)
                    .status(FriendshipStatus.ACCEPTED).build());
            // Lời mời PENDING — cố ý để chứng minh nó KHÔNG được tính là bạn bè
            friendshipRepository.save(Friendship.builder().sender(strangerAccount).receiver(authorAccount)
                    .status(FriendshipStatus.PENDING).build());

            Group open = groupRepository.save(Group.builder()
                    .name("authz-nhom-cong-khai").type(GroupType.PUBLIC).coverUrl("").build());
            Group closed = groupRepository.save(Group.builder()
                    .name("authz-nhom-rieng-tu").type(GroupType.PRIVATE).coverUrl("").build());
            publicGroup = open.getId();
            privateGroup = closed.getId();

            for (Group group : List.of(open, closed)) {
                groupMemberRepository.save(GroupMember.builder()
                        .groupId(group.getId()).accountId(memberAccount.getId())
                        .role(GroupMemberRole.MEMBER).joinedAt(LocalDateTime.now()).build());
            }
        });
    }

    private Account account(String name) {
        return accountRepository.save(Account.builder()
                .email(name + "@authz.local").username(name).password("x").isBanned(0).build());
    }

    private Integer personalPost(PostScope scope) {
        return postService.create(author, "personal-" + scope + "-" + System.nanoTime(), null,
                scope.name(), List.of()).postId();
    }

    private Integer groupPost(Integer groupId, Integer accountId) {
        return postService.create(accountId, "group-" + groupId + "-" + System.nanoTime(), groupId,
                PostScope.PUBLIC.name(), List.of()).postId();
    }

    private Integer comment(Integer accountId, Integer postId, Integer replyTo) {
        return commentService.create(accountId, postId, replyTo, "xin chao", "").commentId();
    }

    @Nested
    @DisplayName("Tạo bài trong group")
    class TaoBaiTrongGroup {

        @Test
        @DisplayName("nhóm PUBLIC mở cho cả người ngoài nhóm")
        void nhom_public_mo_cho_tat_ca() {
            Integer postId = groupPost(publicGroup, stranger);
            assertThat(postId).isPositive();
            assertThat(postService.getOneById(postId, null)).isNotNull();
        }

        @Test
        @DisplayName("nhóm PRIVATE: thành viên đăng được")
        void thanh_vien_dang_duoc() {
            assertThat(groupPost(privateGroup, groupMember)).isPositive();
        }

        @Test
        @DisplayName("nhóm PRIVATE: người ngoài KHÔNG đăng được — đây là lỗ hổng cũ")
        void nguoi_ngoai_khong_dang_duoc() {
            // Trước bản vá, create() chỉ nạp group rồi gắn vào bài mà không hỏi tư cách thành viên,
            // nên lệnh này thành công và bài hiện ra với cả nhóm.
            assertThatThrownBy(() -> groupPost(privateGroup, stranger))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("bài của thành viên không lọt ra ngoài nhóm")
        void bai_khong_lot_ra_ngoai() {
            Integer postId = groupPost(privateGroup, groupMember);

            assertThatThrownBy(() -> postService.getOneById(postId, null))
                    .isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> postService.getOneById(postId, stranger))
                    .isInstanceOf(NotFoundException.class);
            assertThat(postService.getOneById(postId, groupMember)).isNotNull();
        }

        @Test
        @DisplayName("groupId không tồn tại thì báo lỗi, không âm thầm thành bài cá nhân")
        void group_khong_ton_tai_bao_loi() {
            // Hành vi cũ gán group = null, biến bài người dùng tưởng đang đăng trong nhóm thành bài
            // cá nhân PUBLIC — tức là đẩy nội dung ra ngoài.
            assertThatThrownBy(() -> groupPost(999_999, author)).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Ràng buộc scope")
    class RangBuocScope {

        @Test
        @DisplayName("bài nhóm không nhận scope FRIEND")
        void bai_nhom_khong_nhan_FRIEND() {
            assertThatThrownBy(() -> postService.create(groupMember, "scope sai", publicGroup,
                    PostScope.FRIEND.name(), List.of())).isInstanceOf(BadRequestException.class);
        }

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
            assertThatThrownBy(() -> postService.create(author, "x", null, "EVERYONE", List.of()))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    @Nested
    @DisplayName("Sửa bài")
    class SuaBai {

        @Test
        @DisplayName("scope xét theo group thật, không theo body")
        void scope_xet_theo_group_that() {
            Integer postId = groupPost(publicGroup, groupMember);
            // Request không hề nhắc tới groupId — trước bản vá controller coi đây là bài cá nhân và
            // cho phép FRIEND.
            assertThatThrownBy(() -> postService.update(postId, groupMember, "sua noi dung",
                    PostScope.FRIEND.name(), List.of(), List.of()))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("người không sở hữu bài không sửa được")
        void nguoi_la_khong_sua_duoc() {
            Integer postId = personalPost(PostScope.PUBLIC);
            assertThatThrownBy(() -> postService.update(postId, stranger, "cuop bai",
                    PostScope.PUBLIC.name(), List.of(), List.of()))
                    .isInstanceOf(ForbiddenException.class);
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
        @DisplayName("bài trong nhóm PRIVATE — chỉ thành viên bình luận được")
        void bai_nhom_private_chi_thanh_vien() {
            Integer postId = groupPost(privateGroup, groupMember);
            assertThat(comment(groupMember, postId, null)).isPositive();
            assertThatThrownBy(() -> comment(stranger, postId, null)).isInstanceOf(BadRequestException.class);
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
            // Kịch bản tấn công: lấy replyCommentId từ một bài PRIVATE của người khác, ghép với
            // postId của một bài công khai. Kiểm tra quyền chạy trên bài công khai, nhưng comment
            // cha lại nằm ở bài riêng tư — tức là kiểm nhầm tài nguyên.
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

        @Test
        @DisplayName("bài PUBLIC — ai cũng thả tim được")
        void bai_public_ai_cung_tha_tim_duoc() {
            PostDtos.ReactResponse response = postService.like(personalPost(PostScope.PUBLIC), stranger);
            assertThat(response.message()).isEqualTo("Like post successfully");
        }

        @Test
        @DisplayName("bài FRIEND — bạn bè được, người lạ không")
        void bai_friend_chi_ban_be() {
            Integer postId = personalPost(PostScope.FRIEND);
            assertThat(postService.like(postId, friend).message()).isEqualTo("Like post successfully");
            assertThatThrownBy(() -> postService.like(postId, stranger))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("bài PRIVATE — chỉ tác giả")
        void bai_private_chi_tac_gia() {
            Integer postId = personalPost(PostScope.PRIVATE);
            assertThat(postService.like(postId, author).message()).isEqualTo("Like post successfully");
            assertThatThrownBy(() -> postService.like(postId, stranger))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("bài trong nhóm PRIVATE — chỉ thành viên")
        void bai_nhom_private_chi_thanh_vien() {
            Integer postId = groupPost(privateGroup, groupMember);
            assertThat(postService.like(postId, groupMember).message()).isEqualTo("Like post successfully");
            assertThatThrownBy(() -> postService.like(postId, stranger))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("thả tim hai lần là idempotent, không phải lỗi")
        void tha_tim_hai_lan_idempotent() {
            Integer postId = personalPost(PostScope.PUBLIC);
            postService.like(postId, friend);
            assertThat(postService.like(postId, friend).message())
                    .isEqualTo("You have already liked this post");
            postService.unlike(postId, friend);
            assertThat(postService.unlike(postId, friend).message())
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

            assertThat(inTransaction(() -> postRepository.findVisibleById(postId, stranger))).isEmpty();
            // Chủ bài vẫn nạp được bình thường — bộ lọc không chặn nhầm người có quyền
            assertThat(inTransaction(() -> postRepository.findVisibleById(postId, author))).isPresent();
        }

        @Test
        @DisplayName("bài PUBLIC của người khác vẫn nạp được (=> 403 đúng nghĩa)")
        void bai_public_van_nap_duoc() {
            Integer postId = personalPost(PostScope.PUBLIC);

            Integer ownerId = inTransaction(() -> postRepository.findVisibleById(postId, stranger)
                    .orElseThrow().getAccount().getId());
            assertThat(ownerId).isEqualTo(author);
        }

        @Test
        @DisplayName("bài trong nhóm PRIVATE nạp ra rỗng với người ngoài nhóm")
        void bai_nhom_private_nap_ra_rong() {
            Integer postId = groupPost(privateGroup, groupMember);

            assertThat(inTransaction(() -> postRepository.findVisibleById(postId, stranger))).isEmpty();
            assertThat(inTransaction(() -> postRepository.findVisibleById(postId, groupMember))).isPresent();
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

            assertThat(postService.getAll(null, null, stranger))
                    .noneMatch(post -> post.postId().equals(postId));

            assertThat(postService.getSuggestList(stranger))
                    .noneMatch(post -> post.postId().equals(postId));

            assertThatThrownBy(() -> commentService.getAllFromPost(postId, stranger))
                    .isInstanceOf(BadRequestException.class);
        }
    }
}
