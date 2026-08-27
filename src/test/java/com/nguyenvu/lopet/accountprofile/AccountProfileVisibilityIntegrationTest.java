package com.nguyenvu.lopet.accountprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.nguyenvu.lopet.accountprofile.entity.ProfileVisibility;
import com.nguyenvu.lopet.accountprofile.repository.AccountProfileRepository;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.friendship.entity.Friendship;
import com.nguyenvu.lopet.friendship.entity.FriendshipStatus;
import com.nguyenvu.lopet.friendship.repository.FriendshipRepository;
import com.nguyenvu.lopet.support.IntegrationTestBase;

/**
 * Phạm vi hiển thị của hồ sơ tài khoản — xem docs/ACCOUNT_PROFILE_VISIBILITY.md.
 *
 * <p>Bộ lọc chạy ở tầng QUERY ({@code AccountProfileVisibilityFilter}), nên các khẳng định ở đây đi
 * qua service thật chứ không tự dựng lại điều kiện: lọc sau khi đã nạp hồ sơ về là sai, và một test
 * tự so sánh {@code visibility} bằng tay sẽ vẫn xanh khi câu truy vấn hỏng.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Quyền riêng tư hồ sơ tài khoản")
class AccountProfileVisibilityIntegrationTest extends IntegrationTestBase {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> IntegrationTestBase.jdbcUrl("lopet_java_profilevis_test"));
    }

    private static final String RUN = Long.toString(System.nanoTime(), 36);

    @Autowired
    private AccountProfileService profileService;
    @Autowired
    private AccountProfileRepository accountProfileRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private FriendshipRepository friendshipRepository;

    /** Chủ hồ sơ, một người bạn ACCEPTED, một người lạ, và một lời mời PENDING chưa được nhận */
    private Integer owner;
    private Integer friend;
    private Integer stranger;
    private Integer pendingFriend;

    @BeforeAll
    void seed() {
        inTransaction(() -> {
            Account ownerAccount = account("vis-owner");
            Account friendAccount = account("vis-friend");
            Account strangerAccount = account("vis-stranger");
            Account pendingAccount = account("vis-pending");

            owner = ownerAccount.getId();
            friend = friendAccount.getId();
            stranger = strangerAccount.getId();
            pendingFriend = pendingAccount.getId();

            friendshipRepository.save(Friendship.builder().sender(ownerAccount).receiver(friendAccount)
                    .status(FriendshipStatus.ACCEPTED).build());
            // PENDING KHÔNG phải bạn bè — nhánh FRIEND không được tính hàng này
            friendshipRepository.save(Friendship.builder().sender(pendingAccount).receiver(ownerAccount)
                    .status(FriendshipStatus.PENDING).build());
        });
    }

    /**
     * Dựng qua {@link AccountProfileFactory} và gắn vào phía SỞ HỮU của quan hệ 1-1: khoá ngoại nằm ở
     * {@code accounts.account_profile_id}, nên gán {@code profile.account} (phía nghịch, mappedBy) sẽ
     * không ghi được gì xuống DB và {@code findByAccountId} trả rỗng.
     */
    private Account account(String name) {
        String unique = name + "-" + RUN;
        return accountRepository.save(Account.builder()
                .email(unique + "@vis.local").username(unique).password("x").isBanned(0)
                .accountProfile(AccountProfileFactory.seedFor(unique))
                .build());
    }

    private void setVisibility(Integer accountId, ProfileVisibility visibility) {
        profileService.updateMine(accountId, null, null, null, null, null, null, null, null,
                visibility.name());
    }

    /** Người xem có đọc được hồ sơ của {@link #owner} không */
    private boolean visibleTo(Integer viewerId) {
        try {
            return profileService.findVisibleByAccountId(owner, viewerId) != null;
        } catch (NotFoundException exception) {
            return false;
        }
    }

    @Nested
    @DisplayName("Ba mức hiển thị")
    class BaMuc {

        @Test
        @DisplayName("PUBLIC: mọi người đọc được, kể cả khách chưa đăng nhập")
        void public_ai_cung_doc_duoc() {
            setVisibility(owner, ProfileVisibility.PUBLIC);

            assertThat(visibleTo(null)).isTrue();
            assertThat(visibleTo(stranger)).isTrue();
            assertThat(visibleTo(friend)).isTrue();
            assertThat(visibleTo(owner)).isTrue();
        }

        @Test
        @DisplayName("FRIEND: chỉ chính chủ và bạn bè ACCEPTED")
        void friend_chi_ban_be() {
            setVisibility(owner, ProfileVisibility.FRIEND);

            assertThat(visibleTo(null)).as("khách không được xem").isFalse();
            assertThat(visibleTo(stranger)).as("người lạ không được xem").isFalse();
            assertThat(visibleTo(friend)).as("bạn bè ACCEPTED được xem").isTrue();
            assertThat(visibleTo(owner)).as("chính chủ luôn xem được").isTrue();
        }

        @Test
        @DisplayName("PRIVATE: chỉ chính chủ")
        void private_chi_chinh_chu() {
            setVisibility(owner, ProfileVisibility.PRIVATE);

            assertThat(visibleTo(null)).isFalse();
            assertThat(visibleTo(stranger)).isFalse();
            assertThat(visibleTo(friend)).as("PRIVATE hẹp hơn FRIEND").isFalse();
            assertThat(visibleTo(owner)).isTrue();
        }

        /**
         * Lời mời kết bạn chưa được chấp nhận KHÔNG mở được hồ sơ FRIEND. Bỏ điều kiện
         * {@code status = ACCEPTED} thì chỉ cần bấm "kết bạn" là đọc được hồ sơ của bất kỳ ai.
         */
        @Test
        @DisplayName("lời mời kết bạn PENDING không được tính là bạn bè")
        void pending_khong_phai_ban_be() {
            setVisibility(owner, ProfileVisibility.FRIEND);

            assertThat(visibleTo(pendingFriend)).isFalse();
        }

        /** Quan hệ bạn bè hai chiều: nhánh exists phải so cả sender lẫn receiver */
        @Test
        @DisplayName("bạn bè tính cả hai chiều sender/receiver")
        void ban_be_hai_chieu() {
            // owner là SENDER trong hàng friend_ships, friend là RECEIVER — hồ sơ của friend
            // vì thế chỉ mở cho owner nếu truy vấn so cả chiều ngược lại.
            setVisibility(friend, ProfileVisibility.FRIEND);

            assertThat(profileService.findVisibleByAccountId(friend, owner)).isNotNull();
        }
    }

    @Nested
    @DisplayName("Mặc định và cập nhật")
    class CapNhat {

        @Test
        @DisplayName("hồ sơ mới mặc định PUBLIC")
        void mac_dinh_public() {
            assertThat(inTransaction(() -> accountProfileRepository.findByAccountId(stranger).orElseThrow()
                    .getVisibility())).isEqualTo(ProfileVisibility.PUBLIC);
        }

        @Test
        @DisplayName("giá trị lạ bị từ chối thay vì âm thầm về PUBLIC")
        void gia_tri_la_bi_tu_choi() {
            assertThatThrownBy(() -> profileService.updateMine(owner, null, null, null, null, null,
                    null, null, null, "EVERYONE"))
                    .isInstanceOf(BadRequestException.class);
        }

        /**
         * Form multipart gửi ô không điền dưới dạng chuỗi rỗng. Cột là NOT NULL nên ghi thẳng giá trị
         * đã parse (null) vào đó sẽ vỡ ràng buộc — phải giữ nguyên giá trị cũ.
         */
        @Test
        @DisplayName("chuỗi rỗng giữ nguyên giá trị cũ, không ghi NULL")
        void chuoi_rong_giu_nguyen() {
            setVisibility(owner, ProfileVisibility.PRIVATE);

            profileService.updateMine(owner, null, null, "chi sua bio", null, null, null, null, null, "");

            assertThat(inTransaction(() -> accountProfileRepository.findByAccountId(owner).orElseThrow()
                    .getVisibility())).isEqualTo(ProfileVisibility.PRIVATE);
        }
    }

    @Nested
    @DisplayName("Không dò được sự tồn tại")
    class KhongDoDuoc {

        /**
         * Hồ sơ bị ẩn và tài khoản không tồn tại phải cho ra CÙNG một phản hồi. Khác nhau thì endpoint
         * này thành công cụ liệt kê tài khoản.
         */
        @Test
        @DisplayName("hồ sơ ẩn và tài khoản không tồn tại đều ném NotFound")
        void an_va_khong_ton_tai_giong_nhau() {
            setVisibility(owner, ProfileVisibility.PRIVATE);

            assertThatThrownBy(() -> profileService.findVisibleByAccountId(owner, stranger))
                    .isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> profileService.findVisibleByAccountId(-1, stranger))
                    .isInstanceOf(NotFoundException.class);
        }

        /** Hồ sơ người khác cố ý hẹp hơn hồ sơ của chính mình */
        @Test
        @DisplayName("hồ sơ người khác không trả số điện thoại / ngày sinh / quê quán")
        void ho_so_nguoi_khac_hep_hon() {
            setVisibility(owner, ProfileVisibility.PUBLIC);

            var duocXem = profileService.findVisibleByAccountId(owner, stranger);
            assertThat(duocXem.username()).isNotBlank();
            assertThat(duocXem.fullName()).isNotBlank();
            // PublicProfile chỉ có 7 trường; không có chỗ nào để lộ phoneNumber/dateOfBirth/hometown
            assertThat(duocXem.accountId()).isEqualTo(owner);
        }
    }
}
