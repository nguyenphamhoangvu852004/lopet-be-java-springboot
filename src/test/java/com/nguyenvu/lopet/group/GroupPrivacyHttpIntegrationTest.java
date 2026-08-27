package com.nguyenvu.lopet.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.group.entity.GroupMemberStatus;
import com.nguyenvu.lopet.group.repository.GroupMemberRepository;
import com.nguyenvu.lopet.security.jwt.JwtService;
import com.nguyenvu.lopet.security.jwt.UserPrincipal;
import com.nguyenvu.lopet.support.IntegrationTestBase;

/**
 * Quyền riêng tư của nhóm qua ĐÚNG chuỗi HTTP thật: filter JWT → {@code AuthInterceptor} →
 * controller.
 *
 * <p><b>Vì sao phải có bộ test này.</b> Các test khác của module nhóm gọi thẳng service và truyền
 * id người xem vào tham số, tức là ĐI VÒNG qua tầng xác thực. Điều đó tạo ra một điểm mù: mọi test
 * service xanh trong khi route HTTP thật vỡ, vì danh tính người xem không bao giờ tới được
 * controller. Biểu hiện thực tế là người vừa TẠO một nhóm PRIVATE vẫn bị hiện nút "gửi yêu cầu
 * tham gia" vào nhóm của chính mình.
 *
 * <p>Vì vậy ở đây danh tính người xem chỉ đến từ header {@code Authorization}, đúng như client thật
 * gửi.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureMockMvc
@DisplayName("Quyền riêng tư nhóm — qua HTTP thật")
class GroupPrivacyHttpIntegrationTest extends IntegrationTestBase {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> IntegrationTestBase.jdbcUrl("lopet_java_groupprivacy_test"));
        // Redis riêng: id các bản ghi của lớp này trùng id của các lớp test khác.
        // Xem IntegrationTestBase.redisUrl.
        registry.add("spring.data.redis.url", () -> IntegrationTestBase.redisUrl(3));
    }

    private static final String RUN = Long.toString(System.nanoTime(), 36);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private GroupService groupService;
    @Autowired
    private GroupMemberRepository groupMemberRepository;
    @Autowired
    private AccountRepository accountRepository;

    private Integer ownerAccount;
    private Integer outsiderAccount;
    private String ownerToken;
    private String outsiderToken;
    private Integer privateGroup;
    private Integer publicGroup;

    @BeforeAll
    void seed() throws Exception {
        inTransaction(() -> {
            Account owner = account("priv-owner");
            Account outsider = account("priv-outsider");
            ownerAccount = owner.getId();
            outsiderAccount = outsider.getId();
        });

        ownerToken = tokenFor(ownerAccount, "priv-owner");
        outsiderToken = tokenFor(outsiderAccount, "priv-outsider");

        // Tạo nhóm qua service (luồng tạo đã có test riêng); thứ đang kiểm là ĐƯỜNG ĐỌC.
        privateGroup = createGroup("PRIVATE");
        publicGroup = createGroup("PUBLIC");
    }

    private Account account(String name) {
        String unique = name + "-" + RUN;
        return accountRepository.save(Account.builder()
                .email(unique + "@priv.local").username(unique).password("x").isBanned(0).build());
    }

    private String tokenFor(Integer accountId, String name) {
        return jwtService.generateAccessToken(
                new UserPrincipal(accountId, name + "-" + RUN + "@priv.local", List.of()));
    }

    /**
     * {@code GroupService.create} đọc danh tính từ {@code CurrentUser}, mà ở đây không có request
     * nào — nên dựng nhóm bằng repository và tự đặt hàng OWNER, đúng hình dạng service sinh ra.
     */
    private Integer createGroup(String type) {
        return inTransaction(() -> {
            com.nguyenvu.lopet.group.entity.Group group =
                    com.nguyenvu.lopet.group.entity.Group.builder()
                            .name("priv-" + type + "-" + System.nanoTime())
                            .type(com.nguyenvu.lopet.group.entity.GroupType.valueOf(type))
                            .bio("")
                            .coverUrl("")
                            .build();
            group = groupRepositoryRef().save(group);
            groupMemberRepository.save(com.nguyenvu.lopet.group.entity.GroupMember.builder()
                    .groupId(group.getId())
                    .accountId(ownerAccount)
                    .role(com.nguyenvu.lopet.group.entity.GroupMemberRole.OWNER)
                    .status(GroupMemberStatus.ACTIVE)
                    .joinedAt(java.time.LocalDateTime.now())
                    .build());
            return group.getId();
        });
    }

    @Autowired
    private com.nguyenvu.lopet.group.repository.GroupRepository groupRepository;

    private com.nguyenvu.lopet.group.repository.GroupRepository groupRepositoryRef() {
        return groupRepository;
    }

    @Nested
    @DisplayName("Chủ nhóm đọc nhóm PRIVATE của chính mình")
    class ChuNhom {

        /**
         * ĐÂY LÀ BUG ĐƯỢC BÁO. Trước bản vá danh tính người xem không tới được controller trên route
         * đọc, nên {@code viewerStatus} chỉ có thể là NONE: chủ nhóm bị coi là người ngoài, danh sách
         * thành viên bị che, và giao diện mời họ "gửi yêu cầu tham gia" vào nhóm họ vừa tạo.
         */
        @Test
        @DisplayName("có token -> MEMBER, không bị che")
        void chu_nhom_thay_day_du() throws Exception {
            mockMvc.perform(get("/v1/groups/{id}", privateGroup)
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.viewerStatus").value("MEMBER"))
                    .andExpect(jsonPath("$.data.restricted").value(false))
                    .andExpect(jsonPath("$.data.members.length()").value(1))
                    .andExpect(jsonPath("$.data.members[0].accountId").value(ownerAccount));
        }
    }

    @Nested
    @DisplayName("Người ngoài và khách")
    class NguoiNgoai {

        @Test
        @DisplayName("khách vô danh bị che, nhưng vẫn thấy metadata để tìm được nhóm")
        void khach_bi_che() throws Exception {
            mockMvc.perform(get("/v1/groups/{id}", privateGroup))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.restricted").value(true))
                    .andExpect(jsonPath("$.data.viewerStatus").value("NONE"))
                    .andExpect(jsonPath("$.data.members.length()").value(0))
                    // Số thành viên KHÔNG bí mật: giao diện cần nó để mô tả nhóm
                    .andExpect(jsonPath("$.data.totalMembers").value(1))
                    .andExpect(jsonPath("$.data.name").exists());
        }

        @Test
        @DisplayName("người đã đăng nhập nhưng không phải thành viên vẫn bị che")
        void nguoi_ngoai_bi_che() throws Exception {
            mockMvc.perform(get("/v1/groups/{id}", privateGroup)
                            .header("Authorization", "Bearer " + outsiderToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.viewerStatus").value("NONE"))
                    .andExpect(jsonPath("$.data.restricted").value(true));
        }

        @Test
        @DisplayName("nhóm PUBLIC không bị che với ai cả")
        void nhom_public_khong_bi_che() throws Exception {
            mockMvc.perform(get("/v1/groups/{id}", publicGroup))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.restricted").value(false))
                    .andExpect(jsonPath("$.data.members.length()").value(1));
        }
    }

    /**
     * Chốt chống mạo danh. Danh tính người xem CHỈ được lấy từ token đã ký — nếu một route đọc suy
     * ra người xem từ bất kỳ thứ gì client tự khai thì bất kỳ ai cũng đọc được danh sách thành viên,
     * và qua nhánh nhóm của {@code PostVisibility}, cả nội dung bài viết của mọi nhóm riêng tư.
     */
    @Nested
    @DisplayName("Danh tính người xem chỉ đến từ token")
    class DanhTinhTuToken {

        /** Token rác không được làm gãy một route đọc công khai — nó chỉ mất danh tính */
        @Test
        @DisplayName("token hỏng -> route đọc công khai vẫn 200 và coi là khách")
        void token_rac_khong_lam_gay() throws Exception {
            mockMvc.perform(get("/v1/groups/{id}", publicGroup)
                            .header("Authorization", "Bearer khong-phai-jwt"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("không token -> nhóm PRIVATE vẫn bị che")
        void vo_danh_van_bi_che() throws Exception {
            mockMvc.perform(get("/v1/groups/{id}", privateGroup))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.viewerStatus").value("NONE"))
                    .andExpect(jsonPath("$.data.restricted").value(true))
                    .andExpect(jsonPath("$.data.members.length()").value(0));
        }
    }

    @Nested
    @DisplayName("Hàng PENDING không phải thành viên")
    class HangCho {

        @Test
        @DisplayName("gửi yêu cầu xong vẫn bị che, viewerStatus = PENDING_REQUEST")
        void dang_cho_duyet_van_bi_che() throws Exception {
            Integer group = createGroup("PRIVATE");
            inTransaction(() -> groupMemberRepository.save(
                    com.nguyenvu.lopet.group.entity.GroupMember.builder()
                            .groupId(group)
                            .accountId(outsiderAccount)
                            .role(com.nguyenvu.lopet.group.entity.GroupMemberRole.MEMBER)
                            .status(GroupMemberStatus.PENDING)
                            .joinedAt(java.time.LocalDateTime.now())
                            .build()));

            mockMvc.perform(get("/v1/groups/{id}", group)
                            .header("Authorization", "Bearer " + outsiderToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.viewerStatus").value("PENDING_REQUEST"))
                    .andExpect(jsonPath("$.data.restricted").value(true))
                    // Hàng chờ không được đếm vào số thành viên
                    .andExpect(jsonPath("$.data.totalMembers").value(1));
        }

        @Test
        @DisplayName("được mời mà chưa trả lời -> PENDING_INVITE, vẫn bị che")
        void duoc_moi_chua_tra_loi() throws Exception {
            Integer group = createGroup("PRIVATE");
            inTransaction(() -> groupMemberRepository.save(
                    com.nguyenvu.lopet.group.entity.GroupMember.builder()
                            .groupId(group)
                            .accountId(outsiderAccount)
                            .role(com.nguyenvu.lopet.group.entity.GroupMemberRole.MEMBER)
                            .status(GroupMemberStatus.PENDING)
                            .invitedByAccountId(ownerAccount)
                            .joinedAt(java.time.LocalDateTime.now())
                            .build()));

            mockMvc.perform(get("/v1/groups/{id}", group)
                            .header("Authorization", "Bearer " + outsiderToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.viewerStatus").value("PENDING_INVITE"))
                    .andExpect(jsonPath("$.data.restricted").value(true));
        }
    }

    /** Giữ tham chiếu để javac không cảnh báo field không dùng khi refactor */
    @Test
    @DisplayName("dữ liệu seed hợp lệ")
    void seed_hop_le() {
        assertThat(groupService).isNotNull();
        assertThat(privateGroup).isPositive();
        assertThat(publicGroup).isPositive();
    }
}
