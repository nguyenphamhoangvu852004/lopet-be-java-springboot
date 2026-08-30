package com.nguyenvu.lopet.post;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.nguyenvu.lopet.post.entity.Post;
import com.nguyenvu.lopet.post.entity.PostScope;
import com.nguyenvu.lopet.post.repository.PostRepository;
import com.nguyenvu.lopet.support.IntegrationTestBase;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Quyền riêng tư bài viết — tầng repository")
class PostVisibilityIntegrationTest extends IntegrationTestBase {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> IntegrationTestBase.jdbcUrl("lopet_java_visibility_test"));
    }

    private static final String RUN = Long.toString(System.nanoTime(), 36);

    @Autowired
    private PostRepository postRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private FriendshipRepository friendshipRepository;

    private Integer author;
    private Integer friend;
    private Integer stranger;

    private Integer userPublic;
    private Integer userFriend;
    private Integer userPrivate;

    @BeforeAll
    void seed() {
        inTransaction(() -> postRepository.deleteAll());

        inTransaction(() -> {
            Account authorAccount = account("vis-tacgia");
            Account friendAccount = account("vis-banbe");
            Account strangerAccount = account("vis-nguoila");

            author = authorAccount.getId();
            friend = friendAccount.getId();
            stranger = strangerAccount.getId();

            friendshipRepository.save(Friendship.builder().sender(authorAccount).receiver(friendAccount)
                    .status(FriendshipStatus.ACCEPTED).build());
            friendshipRepository.save(Friendship.builder().sender(strangerAccount).receiver(authorAccount)
                    .status(FriendshipStatus.PENDING).build());

            userPublic = post(authorAccount, "userPublic", PostScope.PUBLIC);
            userFriend = post(authorAccount, "userFriend", PostScope.FRIEND);
            userPrivate = post(authorAccount, "userPrivate", PostScope.PRIVATE);
        });
    }

    private Account account(String name) {
        String unique = name + "-" + RUN;
        return accountRepository.save(Account.builder()
                .email(unique + "@test.local").username(unique).password("x").isBanned(0).build());
    }

    private Integer post(Account owner, String content, PostScope scope) {
        return postRepository.save(Post.builder()
                .account(owner).content(content).postScope(scope).build()).getId();
    }

    private List<String> visibleKeys(Integer viewerId) {
        return inTransaction(() -> postRepository.findAllVisible(viewerId, null).stream()
                .map(Post::getContent)
                .sorted()
                .toList());
    }

    @Test
    @DisplayName("khách chưa đăng nhập chỉ thấy nội dung thực sự công khai")
    void khach_chi_thay_noi_dung_cong_khai() {
        assertThat(visibleKeys(null)).containsExactly("userPublic");
    }

    @Test
    @DisplayName("người lạ đã đăng nhập không thấy bài FRIEND hay PRIVATE")
    void nguoi_la_khong_thay_bai_rieng_tu() {
        assertThat(visibleKeys(stranger)).containsExactly("userPublic");
    }

    @Test
    @DisplayName("lời mời kết bạn PENDING không cho quyền xem bài FRIEND")
    void loi_moi_pending_khong_phai_ban_be() {
        assertThat(visibleKeys(stranger)).doesNotContain("userFriend");
    }

    @Test
    @DisplayName("bạn bè ACCEPTED thấy thêm bài scope FRIEND, nhưng không thấy PRIVATE")
    void ban_be_thay_bai_FRIEND() {
        assertThat(visibleKeys(friend)).containsExactly("userFriend", "userPublic");
    }

    @Test
    @DisplayName("tác giả thấy mọi bài của chính mình, kể cả PRIVATE")
    void tac_gia_thay_moi_bai_cua_minh() {
        assertThat(visibleKeys(author)).containsExactly("userFriend", "userPrivate", "userPublic");
    }

    @Test
    @DisplayName("bài PRIVATE giờ chỉ mình tác giả thấy — không còn đường vòng qua nhóm")
    void bai_private_chi_tac_gia_thay() {
        assertThat(visibleKeys(friend)).doesNotContain("userPrivate");
        assertThat(visibleKeys(stranger)).doesNotContain("userPrivate");
        assertThat(visibleKeys(null)).doesNotContain("userPrivate");
    }

    @Test
    @DisplayName("findVisibleById trả rỗng cho bài PRIVATE khi người xem là khách")
    void chi_tiet_bai_private_an_voi_khach() {
        assertThat(inTransaction(() -> postRepository.findVisibleById(userPrivate, null))).isEmpty();
    }

    @Test
    @DisplayName("findVisibleById trả rỗng cho bài FRIEND khi người xem không phải bạn")
    void chi_tiet_bai_friend_an_voi_nguoi_la() {
        assertThat(inTransaction(() -> postRepository.findVisibleById(userFriend, stranger)))
                .isEmpty();
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
    @DisplayName("findByIdInternal KHÔNG lọc — đường nạp cho kiểm duyệt vẫn thấy bài PRIVATE")
    void nap_noi_bo_khong_loc_quyen_xem() {
        assertThat(inTransaction(() -> postRepository.findByIdInternal(userPrivate))).isPresent();
    }

    @Test
    @DisplayName("vẫn nạp kèm tác giả (tài khoản + hồ sơ) và danh sách like")
    void nap_kem_quan_he_can_thiet() {
        inTransaction(() -> {
            Post post = postRepository.findVisibleById(userPublic, null).orElseThrow();
            assertThat(post.getAccount()).isNotNull();
            assertThat(post.getAccount().getId()).isEqualTo(author);
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
        assertThat(forGuest).containsExactly("userPublic");
    }

    @Test
    @DisplayName("trang cá nhân không lộ bài riêng tư cho người lạ")
    void trang_ca_nhan_khong_lo_bai_rieng_tu() {
        List<String> seenByStranger = inTransaction(() ->
                postRepository.findVisibleByAuthor(author, stranger).stream()
                        .map(Post::getContent).sorted().toList());
        assertThat(seenByStranger).containsExactly("userPublic");

        List<Post> seenByAuthor = inTransaction(() ->
                postRepository.findVisibleByAuthor(author, author));
        assertThat(seenByAuthor).hasSize(3);
    }

    @Test
    @DisplayName("tìm kiếm theo nội dung không lộ bài riêng tư")
    void tim_kiem_khong_lo_bai_rieng_tu() {
        List<Post> found = inTransaction(() ->
                postRepository.findAllVisible(stranger, "userPrivate"));
        assertThat(found).isEmpty();

        List<Post> foundByAuthor = inTransaction(() ->
                postRepository.findAllVisible(author, "userPrivate"));
        assertThat(foundByAuthor).hasSize(1);
    }
}
