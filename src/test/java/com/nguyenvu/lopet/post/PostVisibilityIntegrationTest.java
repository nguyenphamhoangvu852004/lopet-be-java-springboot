package com.nguyenvu.lopet.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
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
import com.nguyenvu.lopet.pet.entity.Pet;
import com.nguyenvu.lopet.pet.repository.PetRepository;
import com.nguyenvu.lopet.post.entity.Post;
import com.nguyenvu.lopet.post.entity.PostScope;
import com.nguyenvu.lopet.post.entity.PostType;
import com.nguyenvu.lopet.post.repository.PostRepository;
import com.nguyenvu.lopet.support.IntegrationTestBase;

/**
 * Bản port của {@code postVisibility.integration.test.ts} — kiểm thử tầng QUERY của quyền riêng tư.
 *
 * <p>Lỗ hổng cũ mà bộ test này khoá lại: trước bản vá, {@code GET /v1/posts} trả về toàn bộ bài
 * viết, gồm cả bài PRIVATE và FRIEND của người lạ, cho cả khách chưa đăng nhập.
 *
 * <p>Sau khi {@code posts.account_id} thành {@code posts.pet_id}, mỗi người xem có HAI danh tính và
 * bộ lọc dùng cả hai: id tài khoản (đồ thị bạn bè, quyền của chính chủ) và id thú cưng đang thao
 * tác (tác giả, tư cách thành viên nhóm). Mọi khẳng định dưới đây vì thế truyền cả cặp.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Quyền riêng tư bài viết — tầng repository")
class PostVisibilityIntegrationTest extends IntegrationTestBase {

    /**
     * Database RIÊNG cho lớp test này. Hai bộ test tích hợp cùng ghi vào một database sẽ nhìn thấy
     * bài viết của nhau, làm mọi khẳng định "người xem thấy ĐÚNG những bài này" mất nghĩa — bản TS
     * cũng tách database theo từng file vì lý do đó.
     */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> IntegrationTestBase.jdbcUrl("lopet_java_visibility_test"));
    }

    /**
     * Hậu tố duy nhất theo từng lần chạy. Database test KHÔNG được dọn giữa các lần chạy, nên seed
     * bằng email cố định thì lần chạy thứ hai vỡ ngay ở {@code @BeforeAll} vì UNIQUE trên
     * {@code accounts.email}.
     */
    private static final String RUN = Long.toString(System.nanoTime(), 36);

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
    private PetRepository petRepository;
    @Autowired
    private PetService petService;

    private Integer author;
    private Integer friend;
    private Integer stranger;
    private Integer groupMember;

    private Integer authorPet;
    private Integer authorSecondPet;
    private Integer friendPet;
    private Integer strangerPet;
    private Integer groupMemberPet;

    private Integer userPublic;
    private Integer userFriend;
    private Integer userPrivate;
    private Integer pubGroupPublic;
    private Integer pubGroupPrivate;
    private Integer privGroupPublic;

    @BeforeAll
    void seed() {
        // Nội dung bài ở đây là chuỗi CỐ ĐỊNH ("userPublic", "pubGroupPublic"…) và mọi khẳng định
        // đều so khớp đúng danh sách đó, nên bộ test này chỉ đúng khi bảng posts chỉ chứa dữ liệu
        // của LẦN CHẠY NÀY. Database test tồn tại qua nhiều lần chạy (ddl-auto: update, không ai
        // xoá), nên không dọn trước là lần thứ hai mỗi bài xuất hiện hai bản và mọi test đỏ với
        // "expected exactly one" — một thất bại chẳng liên quan gì tới quyền riêng tư.
        inTransaction(() -> postRepository.deleteAll());

        inTransaction(() -> {
            Account authorAccount = account("vis-author");
            Account friendAccount = account("vis-friend");
            Account strangerAccount = account("vis-stranger");
            Account memberAccount = account("vis-groupmember");

            author = authorAccount.getId();
            friend = friendAccount.getId();
            stranger = strangerAccount.getId();
            groupMember = memberAccount.getId();

            authorPet = pet(author);
            // Con thứ hai CÙNG CHỦ với tác giả — dùng để chứng minh nhánh E: đổi pet đang thao tác
            // không được làm mất quyền xem nội dung của chính mình.
            authorSecondPet = pet(author);
            friendPet = pet(friend);
            strangerPet = pet(stranger);
            groupMemberPet = pet(groupMember);

            // author <-> friend đã là bạn; author <-> stranger thì chưa
            friendshipRepository.save(Friendship.builder().sender(authorAccount).receiver(friendAccount)
                    .status(FriendshipStatus.ACCEPTED).build());
            // Một lời mời PENDING KHÔNG được tính là bạn bè
            friendshipRepository.save(Friendship.builder().sender(strangerAccount).receiver(authorAccount)
                    .status(FriendshipStatus.PENDING).build());

            Group publicGroup = group("vis-nhom-cong-khai", GroupType.PUBLIC);
            Group privateGroup = group("vis-nhom-rieng-tu", GroupType.PRIVATE);
            for (Group group : List.of(publicGroup, privateGroup)) {
                groupMemberRepository.save(GroupMember.builder()
                        .groupId(group.getId()).petId(groupMemberPet)
                        .role(GroupMemberRole.MEMBER).joinedAt(LocalDateTime.now()).build());
            }

            Pet authorEntity = petRepository.findById(authorPet).orElseThrow();
            userPublic = post(authorEntity, "userPublic", PostScope.PUBLIC, null);
            userFriend = post(authorEntity, "userFriend", PostScope.FRIEND, null);
            userPrivate = post(authorEntity, "userPrivate", PostScope.PRIVATE, null);
            pubGroupPublic = post(authorEntity, "pubGroupPublic", PostScope.PUBLIC, publicGroup);
            pubGroupPrivate = post(authorEntity, "pubGroupPrivate", PostScope.PRIVATE, publicGroup);
            privGroupPublic = post(authorEntity, "privGroupPublic", PostScope.PUBLIC, privateGroup);
        });
    }

    private Account account(String name) {
        String unique = name + "-" + RUN;
        return accountRepository.save(Account.builder()
                .email(unique + "@test.local").username(unique).password("x").isBanned(0).build());
    }

    /**
     * Tạo qua {@link PetService} chứ không dựng thẳng entity: hồ sơ công khai được tạo cùng
     * transaction và handle sinh từ id, nên đây là cách duy nhất giữ đúng bất biến "không tồn tại
     * Pet thiếu PetProfile" mà các truy vấn đọc đều dựa vào.
     */
    private Integer pet(Integer ownerId) {
        return petService.create(ownerId, new PetDtos.CreatePetRequest(
                "vis-pet-" + System.nanoTime(), "DOG", null, "MALE",
                LocalDate.of(2023, 3, 12), "PUBLIC")).petId();
    }

    private Group group(String name, GroupType type) {
        return groupRepository.save(Group.builder().name(name + "-" + RUN).type(type).coverUrl("").build());
    }

    private Integer post(Pet owner, String content, PostScope scope, Group group) {
        Post post = Post.builder().pet(owner).content(content).group(group).postScope(scope)
                .postType(group == null ? PostType.USER : PostType.GROUP).build();
        return postRepository.save(post).getId();
    }

    /** Tên các bài mà người xem đọc được qua getAll, đã sắp xếp để so sánh ổn định */
    private List<String> visibleKeys(Integer viewerId, Integer viewerPetId) {
        return inTransaction(() -> postRepository.findAllVisible(viewerId, viewerPetId, null, null).stream()
                .map(Post::getContent)
                .sorted()
                .toList());
    }

    @Test
    @DisplayName("khách chưa đăng nhập chỉ thấy nội dung thực sự công khai")
    void khach_chi_thay_noi_dung_cong_khai() {
        // Đây chính là lỗ hổng cũ: trước bản vá lệnh này trả về CẢ 6 bài
        assertThat(visibleKeys(null, null)).containsExactly("pubGroupPublic", "userPublic");
    }

    @Test
    @DisplayName("người lạ đã đăng nhập không thấy bài FRIEND hay PRIVATE")
    void nguoi_la_khong_thay_bai_rieng_tu() {
        assertThat(visibleKeys(stranger, strangerPet)).containsExactly("pubGroupPublic", "userPublic");
    }

    @Test
    @DisplayName("bạn bè ACCEPTED thấy thêm bài scope FRIEND, nhưng không thấy PRIVATE")
    void ban_be_thay_bai_FRIEND() {
        assertThat(visibleKeys(friend, friendPet))
                .containsExactly("pubGroupPublic", "userFriend", "userPublic");
    }

    @Test
    @DisplayName("tác giả thấy mọi bài của chính mình, kể cả PRIVATE")
    void tac_gia_thay_moi_bai_cua_minh() {
        assertThat(visibleKeys(author, authorPet)).containsExactly("privGroupPublic", "pubGroupPrivate",
                "pubGroupPublic", "userFriend", "userPrivate", "userPublic");
    }

    @Test
    @DisplayName("đổi sang thú cưng khác cùng chủ vẫn thấy đủ nội dung của mình")
    void chu_tai_khoan_thay_bai_cua_moi_con() {
        // Nhánh E. Không có nó thì người dùng hai thú cưng vừa đổi con là mất luôn quyền sửa/xoá bài
        // của chính mình, vì PostAccessGuard nạp bài qua đúng bộ lọc này.
        assertThat(visibleKeys(author, authorSecondPet)).containsExactly("privGroupPublic",
                "pubGroupPrivate", "pubGroupPublic", "userFriend", "userPrivate", "userPublic");
    }

    @Test
    @DisplayName("chưa chọn thú cưng thì vẫn thấy nội dung của chính tài khoản mình")
    void khong_co_header_pet_van_thay_bai_cua_minh() {
        assertThat(visibleKeys(author, null)).contains("userPrivate", "userFriend");
    }

    @Test
    @DisplayName("thành viên nhóm thấy bài trong nhóm mình, kể cả nhóm PRIVATE")
    void thanh_vien_nhom_thay_bai_trong_nhom() {
        assertThat(visibleKeys(groupMember, groupMemberPet)).containsExactly("privGroupPublic",
                "pubGroupPrivate", "pubGroupPublic", "userPublic");
    }

    @Test
    @DisplayName("tư cách thành viên gắn với PET: chủ chưa cho con nào vào nhóm thì không thấy bài nhóm")
    void tu_cach_thanh_vien_theo_pet() {
        // groupMemberPet là con DUY NHẤT được thêm vào nhóm. Nếu bộ lọc còn xét theo tài khoản thì
        // khẳng định này vỡ ngay khi ai đó đổi gm.petId về gm.accountId.
        assertThat(visibleKeys(stranger, strangerPet)).doesNotContain("pubGroupPrivate", "privGroupPublic");
    }

    @Test
    @DisplayName("bài trong nhóm PRIVATE không lọt ra ngoài dù scope là PUBLIC")
    void nhom_private_manh_hon_scope_bai() {
        assertThat(visibleKeys(stranger, strangerPet)).doesNotContain("privGroupPublic");
        assertThat(visibleKeys(null, null)).doesNotContain("privGroupPublic");
    }

    @Test
    @DisplayName("findVisibleById trả rỗng cho bài PRIVATE khi người xem là khách")
    void chi_tiet_bai_private_an_voi_khach() {
        assertThat(inTransaction(() -> postRepository.findVisibleById(userPrivate, null, null))).isEmpty();
    }

    @Test
    @DisplayName("findVisibleById trả rỗng cho bài FRIEND khi người xem không phải bạn")
    void chi_tiet_bai_friend_an_voi_nguoi_la() {
        assertThat(inTransaction(() -> postRepository.findVisibleById(userFriend, stranger, strangerPet)))
                .isEmpty();
    }

    @Test
    @DisplayName("bài FRIEND hiện với đúng bạn bè, bài PRIVATE hiện với chính tác giả")
    void chi_tiet_bai_hien_dung_nguoi() {
        assertThat(inTransaction(() -> postRepository.findVisibleById(userFriend, friend, friendPet))
                .map(Post::getContent)).contains("userFriend");
        assertThat(inTransaction(() -> postRepository.findVisibleById(userPrivate, author, authorPet))
                .map(Post::getContent)).contains("userPrivate");
    }

    @Test
    @DisplayName("vẫn nạp kèm tác giả (pet + hồ sơ công khai) và danh sách like")
    void nap_kem_quan_he_can_thiet() {
        inTransaction(() -> {
            Post post = postRepository.findVisibleById(userPublic, null, null).orElseThrow();
            // Thiếu quan hệ tác giả thì tầng service ném NullPointerException khi dựng DTO
            assertThat(post.getPet()).isNotNull();
            assertThat(post.getPet().getId()).isEqualTo(authorPet);
            // Hồ sơ công khai nằm trong đồ thị nạp: mọi DTO bài viết hiển thị tác giả bằng handle
            assertThat(post.getPet().getPetProfile()).isNotNull();
            assertThat(post.getPet().getAccount().getId()).isEqualTo(author);
            assertThat(post.getPostLikes()).isNotNull();
        });
    }

    @Test
    @DisplayName("danh sách gợi ý lọc theo đúng người xem")
    void goi_y_loc_theo_nguoi_xem() {
        List<String> forGuest = inTransaction(() -> {
            List<Integer> ids = postRepository.findVisibleIds(null, null, PageRequest.of(0, 10));
            return postRepository.findAllByIdsWithDetails(ids).stream()
                    .map(Post::getContent).sorted().toList();
        });
        assertThat(forGuest).containsExactly("pubGroupPublic", "userPublic");
    }

    @Test
    @DisplayName("trang cá nhân của thú cưng không lộ bài riêng tư cho người lạ")
    void trang_ca_nhan_khong_lo_bai_rieng_tu() {
        List<String> seenByStranger = inTransaction(() ->
                postRepository.findVisibleByAuthorPet(authorPet, stranger, strangerPet).stream()
                        .map(Post::getContent).sorted().toList());
        assertThat(seenByStranger).containsExactly("pubGroupPublic", "userPublic");

        List<Post> seenByAuthor = inTransaction(() ->
                postRepository.findVisibleByAuthorPet(authorPet, author, authorPet));
        assertThat(seenByAuthor).hasSize(6);
    }

    @Test
    @DisplayName("route theo tài khoản gom bài của mọi thú cưng thuộc tài khoản đó")
    void trang_theo_tai_khoan_gom_moi_pet() {
        List<Post> seenByAuthor = inTransaction(() ->
                postRepository.findVisibleByAuthorAccount(author, author, authorPet));
        assertThat(seenByAuthor).hasSize(6);

        List<String> seenByStranger = inTransaction(() ->
                postRepository.findVisibleByAuthorAccount(author, stranger, strangerPet).stream()
                        .map(Post::getContent).sorted().toList());
        assertThat(seenByStranger).containsExactly("pubGroupPublic", "userPublic");
    }

    @Test
    @DisplayName("lọc theo groupId không mở được nhóm PRIVATE")
    void loc_groupId_khong_mo_nhom_private() {
        Integer privateGroupId = inTransaction(() ->
                postRepository.findByIdInternal(privGroupPublic).orElseThrow().getGroup().getId());

        List<Post> forStranger = inTransaction(() ->
                postRepository.findAllVisible(stranger, strangerPet, null, privateGroupId));
        assertThat(forStranger).isEmpty();
    }

    @Test
    @DisplayName("tìm kiếm theo nội dung không lộ bài riêng tư")
    void tim_kiem_khong_lo_bai_rieng_tu() {
        List<Post> found = inTransaction(() ->
                postRepository.findAllVisible(stranger, strangerPet, "userPrivate", null));
        assertThat(found).isEmpty();

        List<Post> foundByAuthor = inTransaction(() ->
                postRepository.findAllVisible(author, authorPet, "userPrivate", null));
        assertThat(foundByAuthor).hasSize(1);
    }
}
