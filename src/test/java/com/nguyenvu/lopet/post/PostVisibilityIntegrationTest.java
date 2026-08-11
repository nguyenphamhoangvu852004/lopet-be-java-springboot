package com.nguyenvu.lopet.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.data.domain.PageRequest;

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

    private Integer userPublic;
    private Integer userFriend;
    private Integer userPrivate;
    private Integer pubGroupPublic;
    private Integer pubGroupPrivate;
    private Integer privGroupPublic;

    @BeforeAll
    void seed() {
        inTransaction(() -> {
            Account authorAccount = account("vis-author");
            Account friendAccount = account("vis-friend");
            Account strangerAccount = account("vis-stranger");
            Account memberAccount = account("vis-groupmember");

            author = authorAccount.getId();
            friend = friendAccount.getId();
            stranger = strangerAccount.getId();
            groupMember = memberAccount.getId();

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
                        .groupId(group.getId()).accountId(memberAccount.getId())
                        .role(GroupMemberRole.MEMBER).joinedAt(LocalDateTime.now()).build());
            }

            userPublic = post(authorAccount, "userPublic", PostScope.PUBLIC, null);
            userFriend = post(authorAccount, "userFriend", PostScope.FRIEND, null);
            userPrivate = post(authorAccount, "userPrivate", PostScope.PRIVATE, null);
            pubGroupPublic = post(authorAccount, "pubGroupPublic", PostScope.PUBLIC, publicGroup);
            pubGroupPrivate = post(authorAccount, "pubGroupPrivate", PostScope.PRIVATE, publicGroup);
            privGroupPublic = post(authorAccount, "privGroupPublic", PostScope.PUBLIC, privateGroup);
        });
    }

    private Account account(String name) {
        return accountRepository.save(Account.builder()
                .email(name + "@test.local").username(name).password("x").isBanned(0).build());
    }

    private Group group(String name, GroupType type) {
        return groupRepository.save(Group.builder().name(name).type(type).coverUrl("").build());
    }

    private Integer post(Account owner, String content, PostScope scope, Group group) {
        Post post = Post.builder().account(owner).content(content).group(group).postScope(scope)
                .postType(group == null ? PostType.USER : PostType.GROUP).build();
        return postRepository.save(post).getId();
    }

    /** Tên các bài mà người xem đọc được qua getAll, đã sắp xếp để so sánh ổn định */
    private List<String> visibleKeys(Integer viewerId) {
        return inTransaction(() -> postRepository.findAllVisible(viewerId, null, null).stream()
                .map(Post::getContent)
                .sorted()
                .toList());
    }

    @Test
    @DisplayName("khách chưa đăng nhập chỉ thấy nội dung thực sự công khai")
    void khach_chi_thay_noi_dung_cong_khai() {
        // Đây chính là lỗ hổng cũ: trước bản vá lệnh này trả về CẢ 6 bài
        assertThat(visibleKeys(null)).containsExactly("pubGroupPublic", "userPublic");
    }

    @Test
    @DisplayName("người lạ đã đăng nhập không thấy bài FRIEND hay PRIVATE")
    void nguoi_la_khong_thay_bai_rieng_tu() {
        assertThat(visibleKeys(stranger)).containsExactly("pubGroupPublic", "userPublic");
    }

    @Test
    @DisplayName("bạn bè ACCEPTED thấy thêm bài scope FRIEND, nhưng không thấy PRIVATE")
    void ban_be_thay_bai_FRIEND() {
        assertThat(visibleKeys(friend)).containsExactly("pubGroupPublic", "userFriend", "userPublic");
    }

    @Test
    @DisplayName("tác giả thấy mọi bài của chính mình, kể cả PRIVATE")
    void tac_gia_thay_moi_bai_cua_minh() {
        assertThat(visibleKeys(author)).containsExactly("privGroupPublic", "pubGroupPrivate",
                "pubGroupPublic", "userFriend", "userPrivate", "userPublic");
    }

    @Test
    @DisplayName("thành viên nhóm thấy bài trong nhóm mình, kể cả nhóm PRIVATE")
    void thanh_vien_nhom_thay_bai_trong_nhom() {
        assertThat(visibleKeys(groupMember)).containsExactly("privGroupPublic", "pubGroupPrivate",
                "pubGroupPublic", "userPublic");
    }

    @Test
    @DisplayName("bài trong nhóm PRIVATE không lọt ra ngoài dù scope là PUBLIC")
    void nhom_private_manh_hon_scope_bai() {
        assertThat(visibleKeys(stranger)).doesNotContain("privGroupPublic");
        assertThat(visibleKeys(null)).doesNotContain("privGroupPublic");
    }

    @Test
    @DisplayName("findVisibleById trả rỗng cho bài PRIVATE khi người xem là khách")
    void chi_tiet_bai_private_an_voi_khach() {
        assertThat(inTransaction(() -> postRepository.findVisibleById(userPrivate, null))).isEmpty();
    }

    @Test
    @DisplayName("findVisibleById trả rỗng cho bài FRIEND khi người xem không phải bạn")
    void chi_tiet_bai_friend_an_voi_nguoi_la() {
        assertThat(inTransaction(() -> postRepository.findVisibleById(userFriend, stranger))).isEmpty();
    }

    @Test
    @DisplayName("bài FRIEND hiện với đúng bạn bè, bài PRIVATE hiện với chính tác giả")
    void chi_tiet_bai_hien_dung_nguoi() {
        assertThat(inTransaction(() -> postRepository.findVisibleById(userFriend, friend))
                .map(Post::getContent)).contains("userFriend");
        assertThat(inTransaction(() -> postRepository.findVisibleById(userPrivate, author))
                .map(Post::getContent)).contains("userPrivate");
    }

    @Test
    @DisplayName("vẫn nạp kèm tác giả và danh sách like")
    void nap_kem_quan_he_can_thiet() {
        inTransaction(() -> {
            Post post = postRepository.findVisibleById(userPublic, null).orElseThrow();
            // Thiếu quan hệ tác giả thì tầng service ném NullPointerException khi dựng DTO
            assertThat(post.getAccount()).isNotNull();
            assertThat(post.getAccount().getId()).isEqualTo(author);
            assertThat(post.getPostLikes()).isNotNull();
        });
    }

    @Test
    @DisplayName("danh sách gợi ý lọc theo đúng người xem")
    void goi_y_loc_theo_nguoi_xem() {
        List<String> forGuest = inTransaction(() -> {
            List<Integer> ids = postRepository.findVisibleIds(null, PageRequest.of(0, 10));
            return postRepository.findAllByIdsWithDetails(ids).stream()
                    .map(Post::getContent).sorted().toList();
        });
        assertThat(forGuest).containsExactly("pubGroupPublic", "userPublic");
    }

    @Test
    @DisplayName("trang cá nhân của tác giả không lộ bài riêng tư cho người lạ")
    void trang_ca_nhan_khong_lo_bai_rieng_tu() {
        List<String> seenByStranger = inTransaction(() ->
                postRepository.findVisibleByAuthor(author, stranger).stream()
                        .map(Post::getContent).sorted().toList());
        assertThat(seenByStranger).containsExactly("pubGroupPublic", "userPublic");

        List<Post> seenByAuthor = inTransaction(() -> postRepository.findVisibleByAuthor(author, author));
        assertThat(seenByAuthor).hasSize(6);
    }

    @Test
    @DisplayName("lọc theo groupId không mở được nhóm PRIVATE")
    void loc_groupId_khong_mo_nhom_private() {
        Integer privateGroupId = inTransaction(() ->
                postRepository.findByIdInternal(privGroupPublic).orElseThrow().getGroup().getId());

        List<Post> forStranger = inTransaction(() ->
                postRepository.findAllVisible(stranger, null, privateGroupId));
        assertThat(forStranger).isEmpty();
    }

    @Test
    @DisplayName("tìm kiếm theo nội dung không lộ bài riêng tư")
    void tim_kiem_khong_lo_bai_rieng_tu() {
        List<Post> found = inTransaction(() -> postRepository.findAllVisible(stranger, "userPrivate", null));
        assertThat(found).isEmpty();

        List<Post> foundByAuthor = inTransaction(() ->
                postRepository.findAllVisible(author, "userPrivate", null));
        assertThat(foundByAuthor).hasSize(1);
    }
}
