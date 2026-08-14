package com.nguyenvu.lopet.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.auth.AuthService;
import com.nguyenvu.lopet.auth.dto.RegisterRequest;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.email.OtpStore;
import com.nguyenvu.lopet.profile.dto.ProfileDtos;
import com.nguyenvu.lopet.profile.entity.Profile;
import com.nguyenvu.lopet.profile.repository.ProfileRepository;
import com.nguyenvu.lopet.support.IntegrationTestBase;

/**
 * Refactor module Profile ở tầng SERVICE, trên MySQL thật.
 *
 * <p>Ba thứ được canh ở đây, và cả ba đều là hồi quy chứ không phải tính năng mới:
 * <ul>
 *   <li>hồ sơ phải được cấp trên CẢ HAI đường tạo tài khoản (đăng ký thường và bootstrap admin) —
 *       vá một đường là vẫn còn tài khoản không có hồ sơ;</li>
 *   <li>cập nhật không kèm file KHÔNG được xoá ảnh — đó là bug của bản PATCH cũ;</li>
 *   <li>không đường nào sửa được hồ sơ của người khác.</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Profile Refactor")
class ProfileRefactorIntegrationTest extends IntegrationTestBase {

    private static final String RUN = Long.toString(System.nanoTime(), 36);
    private static final String ADMIN_EMAIL = "profile-admin@lopet.local";
    private static final String ADMIN_USERNAME = "profile-admin";

    /**
     * Database riêng, và BẬT {@code lopet.admin.*} (application-test.yml cố ý để rỗng). Bật lên thì
     * {@code StartupRunner} chạy {@code AdminInitializer.init()} lúc dựng context — đó chính là
     * đường tạo tài khoản thứ hai mà bài test này cần kiểm.
     */
    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> IntegrationTestBase.jdbcUrl("lopet_java_profile_test"));
        registry.add("lopet.admin.email", () -> ADMIN_EMAIL);
        registry.add("lopet.admin.username", () -> ADMIN_USERNAME);
        registry.add("lopet.admin.password", () -> "admin-password");
    }

    @Autowired
    private AuthService authService;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private ProfileRepository profileRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private OtpStore otpStore;

    /** Đăng ký thật qua AuthService — register() đòi cờ OTP nên phải đặt cờ trước */
    private Integer register(String name) {
        String unique = name + "-" + RUN;
        String email = unique + "@profile.local";
        otpStore.markVerified(email);
        return authService.register(
                new RegisterRequest(email, unique, "password123", "password123")).id();
    }

    private Profile profileOf(Integer accountId) {
        return inTransaction(() -> profileRepository.findByAccountId(accountId).orElse(null));
    }

    @Nested
    @DisplayName("Hồ sơ được cấp lúc tạo tài khoản")
    class CapHoSo {

        @Test
        @DisplayName("đăng ký xong là đã có hồ sơ, mang đúng dữ liệu seed")
        void dangKyTaoLuonHoSo() {
            Integer accountId = register("seed-user");

            Profile profile = profileOf(accountId);
            assertThat(profile).as("đăng ký phải cấp sẵn hồ sơ, không để người dùng tự tạo").isNotNull();
            assertThat(profile.getFullName()).isEqualTo("seed-user-" + RUN);
            assertThat(profile.getBio()).isEqualTo("Xin chào, mình là seed-user-" + RUN);
            assertThat(profile.getPhoneNumber()).isEmpty();
            assertThat(profile.getHometown()).isEmpty();
            assertThat(profile.getAvatarUrl()).isEmpty();
            assertThat(profile.getCoverUrl()).isEmpty();
            assertThat(profile.getSex()).isNull();
            assertThat(profile.getDateOfBirth()).isNull();
        }

        /** Đường tạo tài khoản THỨ HAI: AdminInitializer gọi thẳng accountRepository, không qua register() */
        @Test
        @DisplayName("tài khoản admin do bootstrap tạo cũng có hồ sơ")
        void adminBootstrapCungCoHoSo() {
            Integer adminId = inTransaction(() -> accountRepository.findDetailByEmail(ADMIN_EMAIL)
                    .orElseThrow(() -> new AssertionError("StartupRunner chưa tạo tài khoản admin"))
                    .getId());

            assertThat(profileOf(adminId))
                    .as("vá register() mà quên AdminInitializer thì admin vẫn rơi vào trạng thái không hồ sơ")
                    .isNotNull();
        }

        @Test
        @DisplayName("không còn API nào tạo hồ sơ rời — mọi hồ sơ đều gắn với một tài khoản")
        void khongConHoSoMoCoi() {
            register("no-orphan");

            long moCoi = inTransaction(() -> profileRepository.findAll().stream()
                    .filter(profile -> profile.getAccount() == null)
                    .count());

            assertThat(moCoi).as("hồ sơ mồ côi vĩnh viễn không sửa được vì không có chủ").isZero();
        }
    }

    @Nested
    @DisplayName("Cập nhật hồ sơ của chính mình")
    class CapNhat {

        @Test
        @DisplayName("trường không gửi thì giữ nguyên (ngữ nghĩa merge)")
        void chiDoiTruongDuocGui() {
            Integer accountId = register("merge-user");
            profileService.updateMine(accountId, "Tên Ban Đầu", "0900000000", "Bio ban đầu",
                    null, null, LocalDate.of(2000, 1, 15), "Đà Nẵng", 1);

            ProfileDtos.ProfileEntity sau = profileService.updateMine(accountId, "Tên Mới",
                    null, null, null, null, null, null, null);

            assertThat(sau.fullName()).isEqualTo("Tên Mới");
            assertThat(sau.bio()).isEqualTo("Bio ban đầu");
            assertThat(sau.phoneNumber()).isEqualTo("0900000000");
            assertThat(sau.hometown()).isEqualTo("Đà Nẵng");
            assertThat(sau.dateOfBirth()).isEqualTo(LocalDate.of(2000, 1, 15));
            assertThat(sau.sex()).isEqualTo(1);
        }

        @Test
        @DisplayName("chuỗi rỗng VẪN ghi đè — người dùng phải xoá được bio của mình")
        void chuoiRongVanGhiDe() {
            Integer accountId = register("clear-bio");
            profileService.updateMine(accountId, null, null, "Bio sẽ bị xoá",
                    null, null, null, null, null);

            ProfileDtos.ProfileEntity sau = profileService.updateMine(accountId, null, null, "",
                    null, null, null, null, null);

            assertThat(sau.bio()).isEmpty();
        }

        /**
         * Hồi quy cho bug của bản PATCH cũ: controller luôn truyền chuỗi rỗng cho avatar/cover khi
         * request không đính file, nên mỗi lần sửa bio là xoá luôn ảnh đại diện.
         */
        @Test
        @DisplayName("cập nhật không kèm file thì KHÔNG xoá ảnh đang có")
        void khongDinhFileThiGiuNguyenAnh() {
            Integer accountId = register("keep-avatar");
            profileService.updateMine(accountId, null, null, null,
                    "https://cdn.local/avatar.png", "https://cdn.local/cover.png", null, null, null);

            // Đúng thứ controller truyền xuống khi người dùng chỉ sửa bio: hai trường ảnh là null
            ProfileDtos.ProfileEntity sau = profileService.updateMine(accountId, null, null,
                    "Chỉ sửa bio thôi", null, null, null, null, null);

            assertThat(sau.avatarUrl()).isEqualTo("https://cdn.local/avatar.png");
            assertThat(sau.coverUrl()).isEqualTo("https://cdn.local/cover.png");
            assertThat(sau.bio()).isEqualTo("Chỉ sửa bio thôi");
        }
    }

    @Nested
    @DisplayName("Cách ly giữa các tài khoản")
    class CachLy {

        /**
         * Trước refactor, {@code POST /v1/profiles/{id}} nhận profileId tuỳ ý và không kiểm sở hữu,
         * nên gắn được hồ sơ của người khác vào tài khoản mình. Giờ hồ sơ chỉ tra được bằng
         * accountId lấy từ token — không còn tham số nào để trỏ sang người khác.
         */
        @Test
        @DisplayName("sửa hồ sơ của mình không đụng tới hồ sơ người khác")
        void suaHoSoMinhKhongDungNguoiKhac() {
            Integer nanNhan = register("victim");
            Integer keLa = register("stranger");
            profileService.updateMine(nanNhan, "Hồ sơ nạn nhân", null, "Bio nạn nhân",
                    "https://cdn.local/victim.png", null, null, null, null);

            profileService.updateMine(keLa, "Hồ sơ kẻ lạ", null, "Bio kẻ lạ", null, null, null, null, null);

            ProfileDtos.ProfileSummary cuaNanNhan = profileService.findByAccountId(nanNhan);
            assertThat(cuaNanNhan.fullName()).isEqualTo("Hồ sơ nạn nhân");
            assertThat(cuaNanNhan.bio()).isEqualTo("Bio nạn nhân");
            assertThat(cuaNanNhan.avatarUrl()).isEqualTo("https://cdn.local/victim.png");
            assertThat(profileService.findByAccountId(keLa).fullName()).isEqualTo("Hồ sơ kẻ lạ");
        }

        @Test
        @DisplayName("mỗi tài khoản có hồ sơ riêng, không dùng chung row")
        void moiTaiKhoanMotHoSoRieng() {
            Integer mot = register("own-a");
            Integer hai = register("own-b");

            assertThat(profileOf(mot).getId()).isNotEqualTo(profileOf(hai).getId());
        }
    }

    @Nested
    @DisplayName("Đọc hồ sơ của chính mình")
    class DocHoSo {

        @Test
        @DisplayName("GET /me trả đúng hồ sơ của người gọi, không cần biết profileId")
        void traDungHoSoCuaNguoiGoi() {
            Integer accountId = register("read-me");
            profileService.updateMine(accountId, "Tên hiển thị", null, null, null, null, null, null, null);

            ProfileDtos.ProfileSummary cuaToi = profileService.findByAccountId(accountId);

            assertThat(cuaToi.id()).isEqualTo(profileOf(accountId).getId());
            assertThat(cuaToi.fullName()).isEqualTo("Tên hiển thị");
        }

        /** Tài khoản cũ chưa backfill: lỗi phải chỉ thẳng sang script migration thay vì 500 khó hiểu */
        @Test
        @DisplayName("tài khoản không có hồ sơ thì cập nhật báo lỗi chỉ rõ cần backfill")
        void thieuHoSoThiBaoLoiRoRang() {
            assertThatThrownBy(() -> profileService.updateMine(-1, "x", null, null,
                    null, null, null, null, null))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("backfill");
        }
    }
}
