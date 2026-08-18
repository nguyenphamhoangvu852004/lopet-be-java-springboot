package com.nguyenvu.lopet.security.petcontext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.MissingPetHeaderException;
import com.nguyenvu.lopet.common.exception.NoPetOwnedException;
import com.nguyenvu.lopet.common.exception.PetNotOwnedException;
import com.nguyenvu.lopet.pet.PetService;
import com.nguyenvu.lopet.pet.dto.PetDtos;
import com.nguyenvu.lopet.security.jwt.UserPrincipal;
import com.nguyenvu.lopet.support.IntegrationTestBase;

/**
 * Cơ chế "pet đang thao tác" — tầng phân quyền thứ ba, bên cạnh {@code @RequirePermission} (hành
 * động) và {@code OwnershipGuard} (tài nguyên).
 *
 * <p>Gọi thẳng {@code preHandle} với request giả thay vì đi qua MockMvc: thứ cần kiểm là LOGIC của
 * interceptor và ba mã lỗi nó phân biệt, không phải việc Spring có định tuyến đúng URL hay không.
 * Đi qua MockMvc sẽ trộn thêm {@code AuthInterceptor}, aspect permission và bộ chuyển đổi lỗi vào
 * cùng một khẳng định.
 *
 * <p>Chạy trên MySQL thật vì cả ba nhánh đều phụ thuộc truy vấn: {@code findOwnerAccountId} phải
 * chịu tác dụng của {@code @SQLRestriction} (pet đã ngừng hoạt động không tra ra chủ), và
 * {@code existsByAccountId} là thứ phân biệt "chưa có pet nào" với "quên gửi header".
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Pet Context (X-Pet-Id)")
class PetContextIntegrationTest extends IntegrationTestBase {

    /** Database RIÊNG: bộ test khác ghi vào cùng DB sẽ làm khẳng định "tài khoản chưa có pet" mất nghĩa */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> IntegrationTestBase.jdbcUrl("lopet_java_petcontext_test"));
        // Redis riêng: lớp này sống hay chết theo cache pet:owner, xem IntegrationTestBase.redisUrl
        registry.add("spring.data.redis.url", () -> IntegrationTestBase.redisUrl(2));
    }

    @Autowired
    private PetContextInterceptor interceptor;
    @Autowired
    private PetOwnerResolver petOwnerResolver;
    @Autowired
    private PetService petService;
    @Autowired
    private AccountRepository accountRepository;

    private static final String RUN = Long.toString(System.nanoTime(), 36);

    private Integer owner;
    private Integer stranger;
    private Integer petless;
    private Integer petId;

    @BeforeAll
    void seed() {
        inTransaction(() -> {
            owner = account("ctx-owner").getId();
            stranger = account("ctx-stranger").getId();
            petless = account("ctx-petless").getId();
        });
        petId = petService.create(owner, new PetDtos.CreatePetRequest(
                "Milo", "DOG", null, "MALE", LocalDate.of(2023, 1, 1), null)).petId();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private Account account(String name) {
        String unique = name + "-" + RUN;
        return accountRepository.save(Account.builder()
                .email(unique + "@ctx.local").username(unique).password("x").isBanned(0).build());
    }

    /**
     * Dựng đúng bối cảnh mà interceptor thấy ở runtime: danh tính trong SecurityContext (do
     * JwtAuthenticationFilter đặt) và request hiện hành trong RequestContextHolder (do Spring MVC
     * đặt) — {@code PetContext} đọc từ cái thứ hai.
     */
    private boolean preHandle(Integer callerId, String header, String handlerName) throws Exception {
        if (callerId != null) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    new UserPrincipal(callerId, "caller@ctx.local", List.of()), null, List.of()));
        }
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (header != null) {
            request.addHeader(PetContextInterceptor.HEADER, header);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        HandlerMethod handler = new HandlerMethod(new Handlers(), Handlers.class.getMethod(handlerName));
        return interceptor.preHandle(request, new MockHttpServletResponse(), handler);
    }

    /** Ba hình dạng endpoint mà interceptor phải phân biệt */
    static class Handlers {
        @RequirePet
        public void needsPetIdentity() {
        }

        @RequireAnyPet
        public void needsAnyPet() {
        }

        public void needsNothing() {
        }
    }

    /**
     * Đây là các route ĐỌC. Header tuỳ chọn, nhưng "tuỳ chọn" không có nghĩa là bị bỏ qua: trước bản
     * vá interceptor thoát ngay ở đây và {@code PetContext.optional()} luôn null trên toàn bộ đường
     * đọc — chủ nhóm bị coi là người ngoài nhóm của chính mình.
     */
    @Nested
    @DisplayName("Endpoint không đánh dấu — header tuỳ chọn nhưng vẫn được xác nhận")
    class KhongDanhDau {

        @Test
        @DisplayName("đi qua không cần header, kể cả khi chưa xác thực")
        void di_qua_tu_do() throws Exception {
            assertThat(preHandle(null, null, "needsNothing")).isTrue();
            assertThat(PetContext.optional()).isNull();
        }

        /** Chính là ca bị vỡ: pet của người gọi PHẢI có mặt trong context ở route đọc */
        @Test
        @DisplayName("header hợp lệ của chính chủ ĐƯỢC dùng, không bị bỏ qua")
        void header_cua_chinh_chu_duoc_dung() throws Exception {
            assertThat(preHandle(owner, petId.toString(), "needsNothing")).isTrue();
            assertThat(PetContext.optional()).isEqualTo(petId);
        }

        /**
         * Chốt chống mạo danh. Không có token thì không so được quyền sở hữu, nên một
         * {@code X-Pet-Id} tự khai không bao giờ được tin — nếu tin, bất kỳ ai cũng đọc được nội dung
         * nhóm riêng tư chỉ bằng cách đoán id của một pet thành viên.
         */
        @Test
        @DisplayName("chưa xác thực mà gửi header của người khác -> BỎ QUA, không mạo danh được")
        void chua_xac_thuc_thi_khong_tin_header() throws Exception {
            assertThat(preHandle(null, petId.toString(), "needsNothing")).isTrue();
            assertThat(PetContext.optional()).isNull();
        }

        @Test
        @DisplayName("pet của người khác -> BỎ QUA, tụt về mức tài khoản")
        void pet_cua_nguoi_khac_bi_bo_qua() throws Exception {
            assertThat(preHandle(stranger, petId.toString(), "needsNothing")).isTrue();
            assertThat(PetContext.optional()).isNull();
        }

        /**
         * Không ném 400 như đường đi chặt: route đọc công khai không nên chết vì một header vốn là
         * tuỳ chọn, và bỏ qua nó chỉ làm người gọi mất phần nội dung riêng của con vật đó.
         */
        @Test
        @DisplayName("header không phải số -> BỎ QUA, không làm route đọc gãy")
        void header_rac_khong_lam_gay() throws Exception {
            assertThat(preHandle(owner, "khong-phai-so", "needsNothing")).isTrue();
            assertThat(PetContext.optional()).isNull();
        }

        @Test
        @DisplayName("pet không tồn tại -> BỎ QUA")
        void pet_khong_ton_tai_bi_bo_qua() throws Exception {
            assertThat(preHandle(owner, "99999999", "needsNothing")).isTrue();
            assertThat(PetContext.optional()).isNull();
        }
    }

    @Nested
    @DisplayName("@RequirePet — cần biết đúng con pet nào")
    class CanDungPet {

        @Test
        @DisplayName("header hợp lệ của chính chủ đi qua, và PetContext mang đúng petId")
        void hop_le_thi_qua() throws Exception {
            assertThat(preHandle(owner, String.valueOf(petId), "needsPetIdentity")).isTrue();
            assertThat(PetContext.require()).isEqualTo(petId);
        }

        @Test
        @DisplayName("thiếu header nhưng tài khoản CÓ pet -> 400, không phải 403")
        void thieu_header_tra_400() {
            // 400 vì đây là request dị dạng. Trả 403 sẽ khiến client tưởng token hết hiệu lực và đá
            // người dùng ra màn hình đăng nhập.
            assertThatThrownBy(() -> preHandle(owner, null, "needsPetIdentity"))
                    .isInstanceOf(MissingPetHeaderException.class)
                    .hasFieldOrPropertyWithValue("code", 400);
        }

        @Test
        @DisplayName("thiếu header và tài khoản CHƯA có pet nào -> 403 kèm hướng dẫn tạo pet")
        void chua_co_pet_tra_403() {
            assertThatThrownBy(() -> preHandle(petless, null, "needsPetIdentity"))
                    .isInstanceOf(NoPetOwnedException.class)
                    .hasFieldOrPropertyWithValue("code", 403)
                    .hasMessageContaining("chưa có thú cưng");
        }

        @Test
        @DisplayName("header không phải số -> 400")
        void header_rac_tra_400() {
            assertThatThrownBy(() -> preHandle(owner, "abc", "needsPetIdentity"))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("pet của người khác -> 403")
        void pet_cua_nguoi_khac_tra_403() {
            assertThatThrownBy(() -> preHandle(stranger, String.valueOf(petId), "needsPetIdentity"))
                    .isInstanceOf(PetNotOwnedException.class);
        }

        @Test
        @DisplayName("pet không tồn tại trả CÙNG một lỗi với pet của người khác")
        void pet_khong_ton_tai_khong_lo_thong_tin() {
            // Phân biệt hai trường hợp này là đủ để quét id và biết pet nào đang tồn tại.
            assertThatThrownBy(() -> preHandle(owner, "999999", "needsPetIdentity"))
                    .isInstanceOf(PetNotOwnedException.class);
        }

        @Test
        @DisplayName("chưa xác thực -> 403, không rò rỉ gì thêm")
        void chua_xac_thuc_tra_403() {
            assertThatThrownBy(() -> preHandle(null, String.valueOf(petId), "needsPetIdentity"))
                    .isInstanceOf(PetNotOwnedException.class);
        }
    }

    @Nested
    @DisplayName("@RequireAnyPet — chỉ cần tài khoản đã có pet")
    class CanBatKyPet {

        @Test
        @DisplayName("đi qua mà KHÔNG cần header")
        void khong_can_header() throws Exception {
            assertThat(preHandle(owner, null, "needsAnyPet")).isTrue();
            // Không có petId nào được đặt: endpoint loại này không hành động nhân danh con nào cả.
            assertThat(PetContext.optional()).isNull();
        }

        @Test
        @DisplayName("tài khoản chưa có pet nào bị chặn")
        void chua_co_pet_bi_chan() {
            assertThatThrownBy(() -> preHandle(petless, null, "needsAnyPet"))
                    .isInstanceOf(NoPetOwnedException.class);
        }

        /**
         * Không đòi header, nhưng gửi thì vẫn dùng — cùng một quy tắc với route đọc, để
         * {@code X-Pet-Id} mang đúng một nghĩa ở mọi nơi thay vì tuỳ loại endpoint.
         */
        @Test
        @DisplayName("gửi header hợp lệ thì vẫn được dùng")
        void header_hop_le_van_duoc_dung() throws Exception {
            assertThat(preHandle(owner, petId.toString(), "needsAnyPet")).isTrue();
            assertThat(PetContext.optional()).isEqualTo(petId);
        }
    }

    @Nested
    @DisplayName("Cache Redis pet:owner")
    class CacheRedis {

        @Test
        @DisplayName("hai lần hỏi liên tiếp cho cùng một kết quả")
        void ket_qua_on_dinh() {
            assertThat(petOwnerResolver.ownerAccountIdOf(petId)).isEqualTo(owner);
            assertThat(petOwnerResolver.ownerAccountIdOf(petId)).isEqualTo(owner);
        }

        @Test
        @DisplayName("pet không tồn tại trả null và KHÔNG được cache lại")
        void khong_cache_gia_tri_am() {
            assertThat(petOwnerResolver.ownerAccountIdOf(999_999)).isNull();
            assertThat(petOwnerResolver.ownerAccountIdOf(999_999)).isNull();
        }

        @Test
        @DisplayName("ngừng hoạt động pet thì cache bị vô hiệu hoá NGAY, không đợi TTL")
        void ngung_hoat_dong_thi_invalidate() {
            Integer tam = petService.create(owner, new PetDtos.CreatePetRequest(
                    "Tạm", "CAT", null, "FEMALE", LocalDate.of(2023, 1, 1), null)).petId();

            // Nạp vào cache trước khi ngừng — nếu invalidate thiếu, lời gọi sau vẫn trả về owner.
            assertThat(petOwnerResolver.ownerAccountIdOf(tam)).isEqualTo(owner);

            petService.deactivate(tam, owner);

            assertThat(petOwnerResolver.ownerAccountIdOf(tam)).isNull();
        }
    }
}
