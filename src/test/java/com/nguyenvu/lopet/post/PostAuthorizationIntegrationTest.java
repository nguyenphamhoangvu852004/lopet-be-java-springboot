package com.nguyenvu.lopet.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import com.nguyenvu.lopet.group.entity.Group;
import com.nguyenvu.lopet.group.entity.GroupMember;
import com.nguyenvu.lopet.group.entity.GroupMemberRole;
import com.nguyenvu.lopet.group.entity.GroupType;
import com.nguyenvu.lopet.group.repository.GroupMemberRepository;
import com.nguyenvu.lopet.group.repository.GroupRepository;
import com.nguyenvu.lopet.pet.PetService;
import com.nguyenvu.lopet.pet.dto.PetDtos;
import com.nguyenvu.lopet.post.dto.PostDtos;
import com.nguyenvu.lopet.post.entity.PostScope;
import com.nguyenvu.lopet.post.repository.PostRepository;
import com.nguyenvu.lopet.security.petcontext.PetContextTestSupport;
import com.nguyenvu.lopet.support.IntegrationTestBase;

/**
 * Bản port của {@code postAuthorization.integration.test.ts} — phía GHI của ma trận phân quyền:
 * tạo bài, bình luận, trả lời, thả tim, sửa scope.
 *
 * <p>{@link PostVisibilityIntegrationTest} đã phủ phía ĐỌC ở tầng query. File này chạy ở tầng
 * SERVICE vì các quy tắc còn lại — "ai được đăng vào group nào", "comment cha có thuộc đúng bài
 * không" — không nằm trong câu truy vấn mà nằm ở PostService/CommentService.
 *
 * <p>Mỗi tài khoản trong bộ test có ĐÚNG MỘT thú cưng, nên các hàm trợ giúp vẫn nhận id tài khoản
 * như trước và tự tra ra pet tương ứng qua {@link #petOf}. Nhờ đó thân các test không đổi và diff
 * so với bản trước khi đổi khoá ngoại đọc được là "cùng những quy tắc đó, chủ thể khác đi".
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

    /** Xem ghi chú cùng tên ở {@code PostVisibilityIntegrationTest} */
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
    private GroupRepository groupRepository;
    @Autowired
    private GroupMemberRepository groupMemberRepository;
    @Autowired
    private FriendshipRepository friendshipRepository;
    @Autowired
    private PetService petService;

    private Integer author;
    private Integer friend;
    private Integer stranger;
    private Integer groupMember;
    private Integer publicGroup;
    private Integer privateGroup;

    /** accountId -> petId. Mỗi tài khoản đúng một thú cưng trong bộ test này. */
    private final Map<Integer, Integer> pets = new HashMap<>();

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

            for (Integer accountId : List.of(author, friend, stranger, groupMember)) {
                pets.put(accountId, petService.create(accountId, new PetDtos.CreatePetRequest(
                        "authz-pet-" + System.nanoTime(), "DOG", null, "MALE",
                        LocalDate.of(2023, 3, 12), "PUBLIC")).petId());
            }

            friendshipRepository.save(Friendship.builder().sender(authorAccount).receiver(friendAccount)
                    .status(FriendshipStatus.ACCEPTED).build());
            // Lời mời PENDING — cố ý để chứng minh nó KHÔNG được tính là bạn bè
            friendshipRepository.save(Friendship.builder().sender(strangerAccount).receiver(authorAccount)
                    .status(FriendshipStatus.PENDING).build());

            Group open = groupRepository.save(Group.builder()
                    .name("authz-nhom-cong-khai-" + RUN).type(GroupType.PUBLIC).coverUrl("").build());
            Group closed = groupRepository.save(Group.builder()
                    .name("authz-nhom-rieng-tu-" + RUN).type(GroupType.PRIVATE).coverUrl("").build());
            publicGroup = open.getId();
            privateGroup = closed.getId();

            // Thành viên nhóm là THÚ CƯNG của memberAccount, không phải bản thân tài khoản
            for (Group group : List.of(open, closed)) {
                groupMemberRepository.save(GroupMember.builder()
                        .groupId(group.getId()).petId(petOf(groupMember))
                        .role(GroupMemberRole.MEMBER).joinedAt(LocalDateTime.now()).build());
            }
        });
    }

    /**
     * {@code PetContext} sống trong request attribute, mà JUnit tái dùng thread giữa các test — bỏ
     * bước dọn thì pet của test trước rò sang test sau và các khẳng định về quyền mất nghĩa.
     */
    @AfterEach
    void clearPetContext() {
        PetContextTestSupport.clear();
    }

    private Account account(String name) {
        String unique = name + "-" + RUN;
        return accountRepository.save(Account.builder()
                .email(unique + "@authz.local").username(unique).password("x").isBanned(0).build());
    }

    private Integer petOf(Integer accountId) {
        return accountId == null ? null : pets.get(accountId);
    }

    /** Nhân danh thú cưng của tài khoản này cho lời gọi service kế tiếp */
    private void actAs(Integer accountId) {
        PetContextTestSupport.actAs(petOf(accountId));
    }

    private Integer personalPost(PostScope scope) {
        actAs(author);
        return postService.create("personal-" + scope + "-" + System.nanoTime(), null,
                scope.name(), List.of()).postId();
    }

    private Integer groupPost(Integer groupId, Integer accountId) {
        actAs(accountId);
        return postService.create("group-" + groupId + "-" + System.nanoTime(), groupId,
                PostScope.PUBLIC.name(), List.of()).postId();
    }

    private Integer comment(Integer accountId, Integer postId, Integer replyTo) {
        actAs(accountId);
        return commentService.create(accountId, postId, replyTo, "xin chao", "").commentId();
    }

    private PostDtos.PostDetail readAs(Integer postId, Integer viewerId) {
        return postService.getOneById(postId, viewerId, petOf(viewerId));
    }

    @Nested
    @DisplayName("Tạo bài trong group")
    class TaoBaiTrongGroup {

        @Test
        @DisplayName("nhóm PUBLIC: thành viên đăng được, và bài đọc được cả khi chưa đăng nhập")
        void nhom_public_thanh_vien_dang_duoc() {
            Integer postId = groupPost(publicGroup, groupMember);
            assertThat(postId).isPositive();
            assertThat(readAs(postId, null)).isNotNull();
        }

        /**
         * ĐỌC bài của nhóm PUBLIC thì mở cho cả khách, nhưng ĐĂNG thì phải tham gia nhóm trước —
         * nếu không thì nút "tham gia nhóm" chẳng thay đổi điều gì.
         */
        @Test
        @DisplayName("nhóm PUBLIC: người ngoài nhóm KHÔNG đăng được")
        void nhom_public_nguoi_ngoai_khong_dang_duoc() {
            actAs(stranger);
            assertThatThrownBy(() -> groupPost(publicGroup, stranger))
                    .isInstanceOf(ForbiddenException.class);
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

            assertThatThrownBy(() -> readAs(postId, null)).isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> readAs(postId, stranger)).isInstanceOf(NotFoundException.class);
            assertThat(readAs(postId, groupMember)).isNotNull();
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
            actAs(groupMember);
            assertThatThrownBy(() -> postService.create("scope sai", publicGroup,
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
            actAs(author);
            assertThatThrownBy(() -> postService.create("x", null, "EVERYONE", List.of()))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    @Nested
    @DisplayName("Tác giả luôn là pet trong PetContext")
    class TacGiaLaPet {

        @Test
        @DisplayName("bài mới mang pet_id của pet đang thao tác")
        void bai_moi_mang_pet_id() {
            Integer postId = personalPost(PostScope.PUBLIC);
            Integer petId = inTransaction(() ->
                    postRepository.findByIdInternal(postId).orElseThrow().getPet().getId());
            assertThat(petId).isEqualTo(petOf(author));
        }

        @Test
        @DisplayName("không có pet trong context thì không đăng bài được")
        void thieu_pet_khong_dang_duoc() {
            // Endpoint quên gắn @RequirePet là lỗi cấu hình, không phải trạng thái hợp lệ — service
            // phải từ chối chứ không âm thầm chọn một con nào đó.
            PetContextTestSupport.clear();
            assertThatThrownBy(() -> postService.create("khong co pet", null,
                    PostScope.PUBLIC.name(), List.of())).isInstanceOf(RuntimeException.class);
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

        @Test
        @DisplayName("quyền sửa thuộc về CHỦ TÀI KHOẢN của pet tác giả")
        void chu_tai_khoan_van_sua_duoc() {
            Integer postId = personalPost(PostScope.PUBLIC);
            // Không actAs con nào cả: update xét sở hữu ở mức tài khoản, nên nó không được phụ thuộc
            // vào việc người dùng đang chọn thú cưng nào.
            PetContextTestSupport.clear();
            assertThat(postService.update(postId, author, "sua lai", PostScope.PUBLIC.name(),
                    List.of(), List.of()).postId()).isEqualTo(postId);
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
        @DisplayName("bình luận mang danh tính của pet, kèm hồ sơ công khai của nó")
        void binh_luan_mang_danh_tinh_pet() {
            Integer postId = personalPost(PostScope.PUBLIC);
            comment(stranger, postId, null);

            actAs(stranger);
            var items = commentService.getAllFromPost(postId, stranger, petOf(stranger)).comments();
            assertThat(items).hasSize(1);
            assertThat(items.getFirst().pet().id()).isEqualTo(petOf(stranger));
            // Hồ sơ lấy từ pet_profiles: handle luôn có giá trị vì PetService tạo hồ sơ cùng transaction
            assertThat(items.getFirst().pet().profile().handle()).isNotBlank();
        }

        @Test
        @DisplayName("không đọc được danh sách bình luận của bài mình không xem được")
        void khong_doc_duoc_binh_luan_bai_rieng_tu() {
            Integer postId = personalPost(PostScope.PRIVATE);
            comment(author, postId, null);

            assertThatThrownBy(() -> commentService.getAllFromPost(postId, stranger, petOf(stranger)))
                    .isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> commentService.getAllFromPost(postId, null, null))
                    .isInstanceOf(BadRequestException.class);
            assertThat(commentService.getAllFromPost(postId, author, petOf(author)).comments()).hasSize(1);
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

        private PostDtos.ReactResponse like(Integer postId, Integer accountId) {
            actAs(accountId);
            return postService.like(postId, accountId);
        }

        private PostDtos.ReactResponse unlike(Integer postId, Integer accountId) {
            actAs(accountId);
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
        @DisplayName("bài trong nhóm PRIVATE — chỉ thành viên")
        void bai_nhom_private_chi_thanh_vien() {
            Integer postId = groupPost(privateGroup, groupMember);
            assertThat(like(postId, groupMember).message()).isEqualTo("Like post successfully");
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
                    postRepository.findVisibleById(postId, stranger, petOf(stranger)))).isEmpty();
            // Chủ bài vẫn nạp được bình thường — bộ lọc không chặn nhầm người có quyền
            assertThat(inTransaction(() ->
                    postRepository.findVisibleById(postId, author, petOf(author)))).isPresent();
        }

        @Test
        @DisplayName("bài PUBLIC của người khác vẫn nạp được (=> 403 đúng nghĩa)")
        void bai_public_van_nap_duoc() {
            Integer postId = personalPost(PostScope.PUBLIC);

            Integer ownerId = inTransaction(() ->
                    postRepository.findVisibleById(postId, stranger, petOf(stranger))
                            .orElseThrow().getPet().getAccount().getId());
            assertThat(ownerId).isEqualTo(author);
        }

        @Test
        @DisplayName("bài trong nhóm PRIVATE nạp ra rỗng với người ngoài nhóm")
        void bai_nhom_private_nap_ra_rong() {
            Integer postId = groupPost(privateGroup, groupMember);

            assertThat(inTransaction(() ->
                    postRepository.findVisibleById(postId, stranger, petOf(stranger)))).isEmpty();
            assertThat(inTransaction(() ->
                    postRepository.findVisibleById(postId, groupMember, petOf(groupMember)))).isPresent();
        }
    }

    @Nested
    @DisplayName("Chống đi vòng qua endpoint khác")
    class ChongDiVong {

        @Test
        @DisplayName("mọi luồng đọc đều che cùng một bài riêng tư")
        void moi_luong_doc_deu_che() {
            Integer postId = personalPost(PostScope.PRIVATE);
            Integer viewerPet = petOf(stranger);

            assertThatThrownBy(() -> postService.getOneById(postId, stranger, viewerPet))
                    .isInstanceOf(NotFoundException.class);

            assertThat(postService.getByAccountId(author, stranger, viewerPet))
                    .noneMatch(post -> post.postId().equals(postId));

            assertThat(postService.getByPetId(petOf(author), stranger, viewerPet))
                    .noneMatch(post -> post.postId().equals(postId));

            assertThat(postService.getAll(null, null, stranger, viewerPet))
                    .noneMatch(post -> post.postId().equals(postId));

            assertThat(postService.getSuggestList(stranger, viewerPet))
                    .noneMatch(post -> post.postId().equals(postId));

            assertThatThrownBy(() -> commentService.getAllFromPost(postId, stranger, viewerPet))
                    .isInstanceOf(BadRequestException.class);
        }
    }
}
