package com.nguyenvu.lopet.pet;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.pet.dto.PetDtos;
import com.nguyenvu.lopet.pet.entity.PetStatus;
import com.nguyenvu.lopet.pet.repository.PetRepository;
import com.nguyenvu.lopet.petprofile.entity.PetProfileStatus;
import com.nguyenvu.lopet.petprofile.entity.PetVisibility;
import com.nguyenvu.lopet.petprofile.repository.PetProfileRepository;
import com.nguyenvu.lopet.security.jwt.UserPrincipal;
import com.nguyenvu.lopet.support.IntegrationTestBase;

/**
 * Pet Core ở tầng SERVICE + guard, trên MySQL thật — cùng khuôn với
 * {@code PostAuthorizationIntegrationTest}.
 *
 * <p>Không mock repository: ba chỗ dễ sai nhất của module này đều nằm trong SQL — mệnh đề lọc quyền
 * xem của {@code PetProfileVisibilityFilter}, việc {@code @SQLRestriction} có thật sự khiến hồ sơ đã
 * ngừng hoạt động biến mất khỏi MỌI luồng đọc hay không, và bất biến "Pet luôn có PetProfile" chỉ
 * chứng minh được khi transaction thật sự rollback. Mock thì không kiểm được dòng nào trong đó.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Pet Management Core")
class PetManagementIntegrationTest extends IntegrationTestBase {

    /** Database RIÊNG: bộ test khác ghi vào cùng DB sẽ làm mọi khẳng định "danh sách đúng bằng" mất nghĩa */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> IntegrationTestBase.jdbcUrl("lopet_java_pet_test"));
    }

    @Autowired
    private PetService petService;
    @Autowired
    private PetAccessGuard petAccessGuard;
    @Autowired
    private PetRepository petRepository;
    @Autowired
    private PetProfileRepository petProfileRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private Integer owner;
    private Integer stranger;

    @BeforeAll
    void seed() {
        inTransaction(() -> {
            owner = account("pet-owner").getId();
            stranger = account("pet-stranger").getId();
        });
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Hậu tố duy nhất theo từng lần chạy. Database test KHÔNG được dọn giữa các lần chạy (không có
     * {@code @Sql} dọn bảng, cũng không drop database), nên seed bằng email cố định thì lần chạy thứ hai
     * vỡ ngay ở {@code @BeforeAll} vì UNIQUE trên {@code accounts.email}.
     */
    private static final String RUN = Long.toString(System.nanoTime(), 36);

    private Account account(String name) {
        String unique = name + "-" + RUN;
        return accountRepository.save(Account.builder()
                .email(unique + "@pet.local").username(unique).password("x").isBanned(0).build());
    }

    /** Nạp danh tính vào SecurityContext đúng cách JwtAuthenticationFilter làm ở runtime */
    private void authenticate(Integer accountId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new UserPrincipal(accountId, "caller@pet.local", List.of()), null, List.of()));
    }

    private PetDtos.CreatePetRequest request(String name, String visibility) {
        return new PetDtos.CreatePetRequest(name, "DOG", "Golden Retriever", "MALE",
                LocalDate.of(2023, 3, 12), visibility);
    }

    private PetDtos.UpdatePetRequest updateRequest(String name) {
        return new PetDtos.UpdatePetRequest(name, "CAT", "Munchkin", "FEMALE", LocalDate.of(2022, 1, 2));
    }

    private Integer createPet(Integer ownerId, String visibility) {
        return petService.create(ownerId, request("pet-" + System.nanoTime(), visibility)).petId();
    }

    @Nested
    @DisplayName("Tạo thú cưng")
    class TaoThuCung {

        @Test
        @DisplayName("người đã đăng nhập tạo được thú cưng")
        void tao_duoc() {
            PetDtos.PetDetail created = petService.create(owner, request("Milo", null));

            assertThat(created.petId()).isPositive();
            assertThat(created.name()).isEqualTo("Milo");
            assertThat(created.status()).isEqualTo(PetStatus.ACTIVE);
            assertThat(created.ownerAccountId()).isEqualTo(owner);
        }

        @Test
        @DisplayName("chủ sở hữu suy ra từ token — client không gửi ownerId")
        void chu_so_huu_tu_token() {
            Integer petId = createPet(owner, null);

            assertThat(inTransaction(() -> petRepository.findOwnerAccountId(petId)))
                    .contains(owner);
        }

        @Test
        @DisplayName("tạo thú cưng tạo LUÔN hồ sơ công khai kèm theo")
        void tao_kem_ho_so_cong_khai() {
            PetDtos.PetDetail created = petService.create(owner, request("Milo", null));

            assertThat(created.profile()).isNotNull();
            assertThat(created.profile().displayName()).isEqualTo("Milo");
            // Bỏ trống visibility thì mặc định PUBLIC
            assertThat(created.profile().visibility()).isEqualTo(PetVisibility.PUBLIC);
            assertThat(created.profile().handle()).isEqualTo("pet_" + created.petId());

            assertThat(inTransaction(() -> petProfileRepository.findByPetId(created.petId()))).isPresent();
        }

        @Test
        @DisplayName("thú cưng và hồ sơ nằm trong CÙNG một transaction")
        void pet_va_ho_so_la_mot_transaction() {
            Integer[] petId = new Integer[1];

            // Rollback ở transaction NGOÀI: nếu create() mở transaction riêng (REQUIRES_NEW) hoặc
            // ghi ngoài transaction, bản ghi pets sẽ sống sót sau lệnh rollback này.
            transactionTemplate.execute(status -> {
                petId[0] = petService.create(owner, request("rollback-milo", null)).petId();
                status.setRollbackOnly();
                return null;
            });

            assertThat(petId[0]).isNotNull();
            assertThat(inTransaction(() -> petRepository.findById(petId[0]))).isEmpty();
            assertThat(inTransaction(() -> petProfileRepository.findByPetId(petId[0]))).isEmpty();
        }

        @Test
        @DisplayName("không tồn tại thú cưng nào thiếu hồ sơ công khai")
        void khong_co_pet_thieu_ho_so() {
            createPet(owner, null);

            Long moCoi = jdbcTemplate.queryForObject("""
                    select count(*) from pets p
                    where not exists (select 1 from pet_profiles pp where pp.pet_id = p.id)
                    """, Long.class);
            assertThat(moCoi).isZero();
        }

        @Test
        @DisplayName("khách chưa đăng nhập không tạo được — danh tính lấy từ token, không từ body")
        void khach_khong_tao_duoc() {
            SecurityContextHolder.clearContext();
            assertThatThrownBy(() -> com.nguyenvu.lopet.security.CurrentUser.require())
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("name toàn khoảng trắng bị từ chối ở tầng service")
        void name_rac_bi_tu_choi() {
            assertThatThrownBy(() -> petService.create(owner,
                    new PetDtos.CreatePetRequest("   ", "DOG", null, "MALE", LocalDate.now(), null)))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("name được cắt khoảng trắng thừa trước khi lưu")
        void name_duoc_cat_khoang_trang() {
            PetDtos.PetDetail created = petService.create(owner, request("  Milo  ", null));
            assertThat(created.name()).isEqualTo("Milo");
        }

        @Test
        @DisplayName("species lạ trả 400 kèm danh sách hợp lệ, không phải 500")
        void species_la_bi_tu_choi() {
            assertThatThrownBy(() -> petService.create(owner,
                    new PetDtos.CreatePetRequest("Milo", "DRAGON", null, "MALE", LocalDate.now(), null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("species");
        }

        @Test
        @DisplayName("gender lạ bị từ chối")
        void gender_la_bi_tu_choi() {
            assertThatThrownBy(() -> petService.create(owner,
                    new PetDtos.CreatePetRequest("Milo", "DOG", null, "ATTACK_HELICOPTER", LocalDate.now(), null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("gender");
        }

        @Test
        @DisplayName("visibility lạ bị từ chối")
        void visibility_la_bi_tu_choi() {
            assertThatThrownBy(() -> petService.create(owner, request("Milo", "EVERYONE")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("visibility");
        }

        @Test
        @DisplayName("species/gender viết thường vẫn nhận")
        void chap_nhan_chu_thuong() {
            PetDtos.PetDetail created = petService.create(owner,
                    new PetDtos.CreatePetRequest("Milo", "dog", null, "male", LocalDate.now(), "public"));
            assertThat(created.species()).isEqualTo(com.nguyenvu.lopet.pet.entity.PetSpecies.DOG);
        }

        @Test
        @DisplayName("ngày sinh ở tương lai bị từ chối ở tầng service")
        void ngay_sinh_tuong_lai_bi_tu_choi() {
            assertThatThrownBy(() -> petService.create(owner,
                    new PetDtos.CreatePetRequest("Milo", "DOG", null, "MALE",
                            LocalDate.now().plusDays(1), null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("dateOfBirth");
        }
    }

    @Nested
    @DisplayName("Đọc và quyền xem")
    class DocVaQuyenXem {

        @Test
        @DisplayName("thú cưng đang tồn tại đọc được")
        void doc_duoc() {
            Integer petId = createPet(owner, PetVisibility.PUBLIC.name());

            PetDtos.PetDetail detail = petService.getOneById(petId, owner);
            assertThat(detail.petId()).isEqualTo(petId);
            assertThat(detail.ownerAccountId()).isEqualTo(owner);
        }

        @Test
        @DisplayName("id không tồn tại trả 404")
        void id_khong_ton_tai() {
            assertThatThrownBy(() -> petService.getOneById(999_999, owner))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("hồ sơ PUBLIC: khách chưa đăng nhập cũng xem được")
        void public_khach_xem_duoc() {
            Integer petId = createPet(owner, PetVisibility.PUBLIC.name());
            assertThat(petService.getOneById(petId, null)).isNotNull();
        }

        @Test
        @DisplayName("hồ sơ PRIVATE: người lạ và khách đều nhận 404, không phải 403")
        void private_an_voi_nguoi_la() {
            Integer petId = createPet(owner, PetVisibility.PRIVATE.name());

            // 404 chứ không phải 403: phản hồi phải giống hệt "không tồn tại", nếu không endpoint
            // này thành công cụ quét id để biết hồ sơ riêng tư nào đang tồn tại.
            assertThatThrownBy(() -> petService.getOneById(petId, stranger))
                    .isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> petService.getOneById(petId, null))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("hồ sơ FOLLOWERS hiện fail CLOSED — chưa có đồ thị follow nên chỉ chủ xem được")
        void followers_fail_closed() {
            Integer petId = createPet(owner, PetVisibility.FOLLOWERS.name());

            assertThatThrownBy(() -> petService.getOneById(petId, stranger))
                    .isInstanceOf(NotFoundException.class);
            assertThat(petService.getOneById(petId, owner)).isNotNull();
        }

        @Test
        @DisplayName("chủ sở hữu xem được thú cưng của mình ở MỌI visibility")
        void chu_so_huu_xem_duoc_moi_visibility() {
            for (PetVisibility visibility : PetVisibility.values()) {
                Integer petId = createPet(owner, visibility.name());
                assertThat(petService.getOneById(petId, owner)).isNotNull();
            }
        }

        @Test
        @DisplayName("danh sách của tôi không chứa thú cưng của người khác")
        void danh_sach_khong_lan_cua_nguoi_khac() {
            Integer cuaNguoiLa = createPet(stranger, PetVisibility.PUBLIC.name());
            Integer cuaToi = createPet(owner, null);

            List<PetDtos.PetListItem> pets = petService.getOwnedBy(owner);
            assertThat(pets).anyMatch(pet -> pet.petId().equals(cuaToi));
            assertThat(pets).noneMatch(pet -> pet.petId().equals(cuaNguoiLa));
        }
    }

    @Nested
    @DisplayName("Sửa thông tin sinh học")
    class SuaThongTin {

        @Test
        @DisplayName("chủ sở hữu sửa được các trường cho phép")
        void chu_so_huu_sua_duoc() {
            Integer petId = createPet(owner, null);

            PetDtos.PetDetail updated = petService.update(petId, owner, updateRequest("Milo mới"));

            assertThat(updated.name()).isEqualTo("Milo mới");
            assertThat(updated.species()).isEqualTo(com.nguyenvu.lopet.pet.entity.PetSpecies.CAT);
            assertThat(updated.gender()).isEqualTo(com.nguyenvu.lopet.pet.entity.PetGender.FEMALE);
            assertThat(updated.breed()).isEqualTo("Munchkin");
        }

        @Test
        @DisplayName("người lạ KHÔNG sửa được dù biết petId")
        void nguoi_la_khong_sua_duoc() {
            Integer petId = createPet(owner, PetVisibility.PUBLIC.name());

            assertThatThrownBy(() -> petService.update(petId, stranger, updateRequest("cướp")))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("sửa không đụng tới status, chủ sở hữu hay createdAt")
        void sua_khong_dung_truong_domain() {
            Integer petId = createPet(owner, null);
            PetDtos.PetDetail before = petService.getOneById(petId, owner);

            PetDtos.PetDetail after = petService.update(petId, owner, updateRequest("Đổi tên"));

            assertThat(after.status()).isEqualTo(PetStatus.ACTIVE);
            assertThat(after.ownerAccountId()).isEqualTo(owner);
            assertThat(after.createdAt()).isEqualTo(before.createdAt());
        }

        @Test
        @DisplayName("sửa thông tin sinh học KHÔNG đổi tên hiển thị của hồ sơ công khai")
        void sua_khong_dung_ho_so_cong_khai() {
            PetDtos.PetDetail created = petService.create(owner, request("Milo", null));

            petService.update(created.petId(), owner, updateRequest("Tên khai sinh mới"));

            PetDtos.PetDetail after = petService.getOneById(created.petId(), owner);
            assertThat(after.name()).isEqualTo("Tên khai sinh mới");
            assertThat(after.profile().displayName()).isEqualTo("Milo");
        }

        @Test
        @DisplayName("guard chặn người lạ trước cả khi vào service")
        void guard_chan_nguoi_la() {
            Integer petId = createPet(owner, PetVisibility.PUBLIC.name());

            authenticate(stranger);
            assertThatThrownBy(() -> petAccessGuard.requireOwnerToEdit(petId))
                    .isInstanceOf(ForbiddenException.class);

            authenticate(owner);
            petAccessGuard.requireOwnerToEdit(petId);
        }

        @Test
        @DisplayName("guard trả 404 cho hồ sơ PRIVATE của người khác, không phải 403")
        void guard_khong_do_duoc_su_ton_tai() {
            Integer petId = createPet(owner, PetVisibility.PRIVATE.name());

            authenticate(stranger);
            assertThatThrownBy(() -> petAccessGuard.requireOwnerToEdit(petId))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("guard từ chối khi chưa xác thực")
        void guard_tu_choi_khach() {
            Integer petId = createPet(owner, PetVisibility.PUBLIC.name());

            SecurityContextHolder.clearContext();
            assertThatThrownBy(() -> petAccessGuard.requireOwnerToEdit(petId))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    @DisplayName("Ngừng hoạt động")
    class NgungHoatDong {

        @Test
        @DisplayName("chủ sở hữu ngừng được, và đó là xoá MỀM")
        void chu_so_huu_ngung_duoc() {
            Integer petId = createPet(owner, null);

            PetDtos.DeactivatePetResponse response = petService.deactivate(petId, owner);
            assertThat(response.status()).isEqualTo(PetStatus.DEACTIVATED);

            // Hàng vẫn còn trong bảng — chỉ bị @SQLRestriction che đi, không bị DELETE
            assertThat(deactivatedRowCount(petId)).isEqualTo(1L);
        }

        @Test
        @DisplayName("ngừng thú cưng thì hồ sơ công khai cũng tắt theo")
        void ho_so_tat_theo() {
            Integer petId = createPet(owner, PetVisibility.PUBLIC.name());
            petService.deactivate(petId, owner);

            assertThat(inTransaction(() -> petProfileRepository.findByPetId(petId))).isEmpty();
            assertThat(deactivatedProfileRowCount(petId)).isEqualTo(1L);
        }

        @Test
        @DisplayName("người lạ KHÔNG ngừng được")
        void nguoi_la_khong_ngung_duoc() {
            Integer petId = createPet(owner, PetVisibility.PUBLIC.name());

            assertThatThrownBy(() -> petService.deactivate(petId, stranger))
                    .isInstanceOf(ForbiddenException.class);
            assertThat(inTransaction(() -> petRepository.findById(petId))).isPresent();
        }

        @Test
        @DisplayName("guard chặn người lạ trước khi vào luồng ngừng hoạt động")
        void guard_chan_ngung() {
            Integer petId = createPet(owner, PetVisibility.PUBLIC.name());

            authenticate(stranger);
            assertThatThrownBy(() -> petAccessGuard.requireOwnerToDeactivate(petId))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("thú cưng đã ngừng biến mất khỏi MỌI luồng đọc")
        void da_ngung_thi_bien_mat() {
            Integer petId = createPet(owner, PetVisibility.PUBLIC.name());
            petService.deactivate(petId, owner);

            assertThatThrownBy(() -> petService.getOneById(petId, owner))
                    .isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> petService.getOneById(petId, null))
                    .isInstanceOf(NotFoundException.class);
            assertThat(petService.getOwnedBy(owner)).noneMatch(pet -> pet.petId().equals(petId));
            assertThat(inTransaction(() -> petRepository.findById(petId))).isEmpty();
        }

        @Test
        @DisplayName("thú cưng đã ngừng không sửa được nữa")
        void da_ngung_thi_khong_sua_duoc() {
            Integer petId = createPet(owner, null);
            petService.deactivate(petId, owner);

            assertThatThrownBy(() -> petService.update(petId, owner, updateRequest("hồi sinh")))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("ngừng hai lần không được — lần hai đã biến mất")
        void ngung_hai_lan() {
            Integer petId = createPet(owner, null);
            petService.deactivate(petId, owner);

            assertThatThrownBy(() -> petService.deactivate(petId, owner))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("pet đã ngừng không còn tra được chủ sở hữu — nền tảng của validate X-Pet-Id")
        void da_ngung_thi_khong_tra_duoc_chu() {
            Integer petId = createPet(owner, null);
            petService.deactivate(petId, owner);

            assertThat(inTransaction(() -> petRepository.findOwnerAccountId(petId))).isEmpty();
        }
    }

    /**
     * Đếm bằng SQL THÔ: mọi truy vấn qua JPA đều bị {@code @SQLRestriction} lọc mất hàng đã xoá mềm,
     * nên không có cách nào khác để phân biệt "xoá mềm" với "xoá cứng" từ phía test.
     */
    private Long deactivatedRowCount(Integer petId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from pets where id = ? and deletedAt is not null and status = ?",
                Long.class, petId, PetStatus.DEACTIVATED.name());
    }

    private Long deactivatedProfileRowCount(Integer petId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from pet_profiles where pet_id = ? and deletedAt is not null and status = ?",
                Long.class, petId, PetProfileStatus.DEACTIVATED.name());
    }
}
