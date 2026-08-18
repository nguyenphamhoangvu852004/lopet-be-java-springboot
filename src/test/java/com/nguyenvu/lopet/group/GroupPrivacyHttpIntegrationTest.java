package com.nguyenvu.lopet.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
import com.nguyenvu.lopet.pet.PetService;
import com.nguyenvu.lopet.pet.dto.PetDtos;
import com.nguyenvu.lopet.security.jwt.JwtService;
import com.nguyenvu.lopet.security.jwt.UserPrincipal;
import com.nguyenvu.lopet.support.IntegrationTestBase;

/**
 * Quyền riêng tư của nhóm qua ĐÚNG chuỗi HTTP thật: filter JWT → {@code AuthInterceptor} →
 * {@code PetContextInterceptor} → controller.
 *
 * <p><b>Vì sao phải có bộ test này.</b> Các test khác của module nhóm gọi thẳng service và đặt pet
 * đang thao tác bằng {@code PetContextTestSupport.actAs()}, tức là ghi thẳng vào request attribute và
 * ĐI VÒNG qua interceptor. Điều đó tạo ra một điểm mù chết người: mọi test service xanh trong khi
 * route HTTP thật vỡ, vì interceptor trước bản vá thoát sớm ở route không mang {@code @RequirePet} và
 * không bao giờ đặt attribute đó. Biểu hiện thực tế là người vừa TẠO một nhóm PRIVATE vẫn bị hiện nút
 * "gửi yêu cầu tham gia" vào nhóm của chính mình.
 *
 * <p>Vì vậy ở đây KHÔNG dùng {@code actAs()}: pet đang thao tác chỉ đến từ header {@code X-Pet-Id},
 * đúng như client thật gửi.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureMockMvc
@DisplayName("Quyền riêng tư nhóm — qua HTTP thật")
class GroupPrivacyHttpIntegrationTest extends IntegrationTestBase {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> IntegrationTestBase.jdbcUrl("lopet_java_groupprivacy_test"));
        // Redis riêng: interceptor xác nhận quyền sở hữu pet qua cache pet:owner, và petId của lớp
        // này trùng petId của các lớp test khác. Xem IntegrationTestBase.redisUrl.
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
    private PetService petService;
    @Autowired
    private AccountRepository accountRepository;

    private Integer ownerAccount;
    private Integer outsiderAccount;
    private String ownerToken;
    private String outsiderToken;
    private Integer ownerPet;
    private Integer outsiderPet;
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
        ownerPet = pet(ownerAccount);
        outsiderPet = pet(outsiderAccount);

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

    private Integer pet(Integer accountId) {
        return petService.create(accountId, new PetDtos.CreatePetRequest(
                "priv-pet-" + System.nanoTime(), "DOG", null, "MALE",
                LocalDate.of(2023, 3, 12), "PUBLIC")).petId();
    }

    /**
     * {@code GroupService.create} đọc pet từ {@code PetContext}, mà ở đây không có request nào —
     * nên dựng nhóm bằng repository và tự đặt hàng OWNER, đúng hình dạng service sinh ra.
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
                    .petId(ownerPet)
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
         * ĐÂY LÀ BUG ĐƯỢC BÁO. Trước bản vá interceptor bỏ qua {@code X-Pet-Id} trên route đọc, nên
         * {@code viewerStatus} chỉ có thể là NONE: chủ nhóm bị coi là người ngoài, danh sách thành
         * viên bị che, và giao diện mời họ "gửi yêu cầu tham gia" vào nhóm họ vừa tạo.
         */
        @Test
        @DisplayName("có token + X-Pet-Id -> MEMBER, không bị che")
        void chu_nhom_thay_day_du() throws Exception {
            mockMvc.perform(get("/v1/groups/{id}", privateGroup)
                            .header("Authorization", "Bearer " + ownerToken)
                            .header("X-Pet-Id", ownerPet))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.viewerStatus").value("MEMBER"))
                    .andExpect(jsonPath("$.data.restricted").value(false))
                    .andExpect(jsonPath("$.data.members.length()").value(1))
                    .andExpect(jsonPath("$.data.members[0].petId").value(ownerPet));
        }

        /**
         * Có token nhưng chưa chọn pet thì vẫn là người ngoài — tư cách thành viên gắn với con vật,
         * không với tài khoản. Đây là hành vi ĐÚNG, không phải bug.
         */
        @Test
        @DisplayName("có token nhưng KHÔNG gửi X-Pet-Id -> vẫn bị che")
        void thieu_header_thi_van_bi_che() throws Exception {
            mockMvc.perform(get("/v1/groups/{id}", privateGroup)
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.viewerStatus").value("NONE"))
                    .andExpect(jsonPath("$.data.restricted").value(true))
                    .andExpect(jsonPath("$.data.members.length()").value(0));
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
                            .header("Authorization", "Bearer " + outsiderToken)
                            .header("X-Pet-Id", outsiderPet))
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
     * Chốt chống mạo danh. Nếu interceptor tin {@code X-Pet-Id} mà không so quyền sở hữu thì bất kỳ ai
     * cũng đọc được danh sách thành viên — và qua nhánh D của {@code PostVisibility}, cả nội dung bài
     * viết — của mọi nhóm riêng tư, chỉ bằng cách đoán id của một pet thành viên.
     */
    @Nested
    @DisplayName("Không mạo danh được bằng X-Pet-Id")
    class KhongMaoDanh {

        @Test
        @DisplayName("không token + X-Pet-Id của thành viên -> vẫn bị che")
        void vo_danh_gui_pet_cua_thanh_vien() throws Exception {
            mockMvc.perform(get("/v1/groups/{id}", privateGroup)
                            .header("X-Pet-Id", ownerPet))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.viewerStatus").value("NONE"))
                    .andExpect(jsonPath("$.data.restricted").value(true))
                    .andExpect(jsonPath("$.data.members.length()").value(0));
        }

        @Test
        @DisplayName("token người ngoài + X-Pet-Id của thành viên -> vẫn bị che")
        void nguoi_ngoai_gui_pet_cua_nguoi_khac() throws Exception {
            mockMvc.perform(get("/v1/groups/{id}", privateGroup)
                            .header("Authorization", "Bearer " + outsiderToken)
                            .header("X-Pet-Id", ownerPet))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.viewerStatus").value("NONE"))
                    .andExpect(jsonPath("$.data.restricted").value(true))
                    .andExpect(jsonPath("$.data.members.length()").value(0));
        }

        /** Header rác không được làm gãy một route đọc công khai */
        @Test
        @DisplayName("X-Pet-Id không phải số -> bỏ qua, route vẫn 200")
        void header_rac_khong_lam_gay() throws Exception {
            mockMvc.perform(get("/v1/groups/{id}", publicGroup)
                            .header("Authorization", "Bearer " + ownerToken)
                            .header("X-Pet-Id", "khong-phai-so"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.viewerStatus").value("NONE"));
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
                            .petId(outsiderPet)
                            .role(com.nguyenvu.lopet.group.entity.GroupMemberRole.MEMBER)
                            .status(GroupMemberStatus.PENDING)
                            .joinedAt(java.time.LocalDateTime.now())
                            .build()));

            mockMvc.perform(get("/v1/groups/{id}", group)
                            .header("Authorization", "Bearer " + outsiderToken)
                            .header("X-Pet-Id", outsiderPet))
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
                            .petId(outsiderPet)
                            .role(com.nguyenvu.lopet.group.entity.GroupMemberRole.MEMBER)
                            .status(GroupMemberStatus.PENDING)
                            .invitedByPetId(ownerPet)
                            .joinedAt(java.time.LocalDateTime.now())
                            .build()));

            mockMvc.perform(get("/v1/groups/{id}", group)
                            .header("Authorization", "Bearer " + outsiderToken)
                            .header("X-Pet-Id", outsiderPet))
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
