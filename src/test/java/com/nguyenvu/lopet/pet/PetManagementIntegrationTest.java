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
import com.nguyenvu.lopet.common.exception.ConflictException;
import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.pet.dto.PetDtos;
import com.nguyenvu.lopet.pet.entity.PetOwnershipType;
import com.nguyenvu.lopet.pet.entity.PetStatus;
import com.nguyenvu.lopet.pet.entity.PetVisibility;
import com.nguyenvu.lopet.pet.repository.PetOwnershipRepository;
import com.nguyenvu.lopet.pet.repository.PetRepository;
import com.nguyenvu.lopet.security.jwt.UserPrincipal;
import com.nguyenvu.lopet.support.IntegrationTestBase;

/**
 * Pet Core ở tầng SERVICE + guard, trên MySQL thật — cùng khuôn với
 * {@code PostAuthorizationIntegrationTest}.
 *
 * <p>Không mock repository: hai chỗ dễ sai nhất của module này đều nằm trong SQL — mệnh đề lọc
 * quyền xem của {@code PetVisibilityFilter}, và việc {@code @SQLRestriction} có thật sự khiến hồ sơ
 * đã lưu trữ biến mất khỏi MỌI luồng đọc hay không. Mock thì không kiểm được dòng nào trong đó.
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
    private PetOwnershipRepository petOwnershipRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private Integer owner;
    private Integer coOwner;
    private Integer secondCoOwner;
    private Integer stranger;

    @BeforeAll
    void seed() {
        inTransaction(() -> {
            owner = account("pet-owner").getId();
            coOwner = account("pet-coowner").getId();
            secondCoOwner = account("pet-coowner-2").getId();
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
                LocalDate.of(2023, 3, 12), "Always hungry.", visibility);
    }

    private PetDtos.UpdatePetRequest updateRequest(String name, String visibility) {
        return new PetDtos.UpdatePetRequest(name, "CAT", "Munchkin", "FEMALE",
                LocalDate.of(2022, 1, 2), "Sleeps a lot.", visibility);
    }

    private Integer createPet(Integer ownerId, String visibility) {
        return petService.create(ownerId, request("pet-" + System.nanoTime(), visibility)).petId();
    }

    @Nested
    @DisplayName("Tạo hồ sơ")
    class TaoHoSo {

        @Test
        @DisplayName("người đã đăng nhập tạo được hồ sơ")
        void tao_duoc_ho_so() {
            PetDtos.PetDetail created = petService.create(owner, request("Milo", null));

            assertThat(created.petId()).isPositive();
            assertThat(created.name()).isEqualTo("Milo");
            assertThat(created.status()).isEqualTo(PetStatus.ACTIVE);
            // Bỏ trống visibility thì mặc định PUBLIC
            assertThat(created.visibility()).isEqualTo(PetVisibility.PUBLIC);
        }

        @Test
        @DisplayName("người tạo tự động thành PRIMARY_OWNER — client không gửi gì cả")
        void nguoi_tao_thanh_primary_owner() {
            Integer petId = createPet(owner, null);

            List<com.nguyenvu.lopet.pet.entity.PetOwnership> ownerships =
                    inTransaction(() -> petOwnershipRepository.findAllByPetId(petId));

            assertThat(ownerships).hasSize(1);
            assertThat(ownerships.get(0).getUserId()).isEqualTo(owner);
            assertThat(ownerships.get(0).getOwnershipType()).isEqualTo(PetOwnershipType.PRIMARY_OWNER);
        }

        @Test
        @DisplayName("hồ sơ và bản ghi sở hữu nằm trong CÙNG một transaction")
        void tao_ho_so_va_so_huu_la_mot_transaction() {
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
            assertThat(inTransaction(() -> petOwnershipRepository.findAllByPetId(petId[0]))).isEmpty();
        }

        @Test
        @DisplayName("không có hồ sơ nào tồn tại mà thiếu bản ghi sở hữu")
        void khong_co_ho_so_mo_coi() {
            Integer petId = createPet(owner, null);
            assertThat(inTransaction(() -> petOwnershipRepository.findAllByPetId(petId))).isNotEmpty();
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
                    new PetDtos.CreatePetRequest("   ", "DOG", null, "MALE", LocalDate.now(), null, null)))
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
                    new PetDtos.CreatePetRequest("Milo", "DRAGON", null, "MALE", LocalDate.now(), null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("species");
        }

        @Test
        @DisplayName("gender lạ bị từ chối")
        void gender_la_bi_tu_choi() {
            assertThatThrownBy(() -> petService.create(owner,
                    new PetDtos.CreatePetRequest("Milo", "DOG", null, "ATTACK_HELICOPTER", LocalDate.now(), null, null)))
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
                    new PetDtos.CreatePetRequest("Milo", "dog", null, "male", LocalDate.now(), null, "public"));
            assertThat(created.species()).isEqualTo(com.nguyenvu.lopet.pet.entity.PetSpecies.DOG);
        }

        @Test
        @DisplayName("ngày sinh ở tương lai bị từ chối ở tầng service")
        void ngay_sinh_tuong_lai_bi_tu_choi() {
            assertThatThrownBy(() -> petService.create(owner,
                    new PetDtos.CreatePetRequest("Milo", "DOG", null, "MALE",
                            LocalDate.now().plusDays(1), null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("dateOfBirth");
        }
    }

    @Nested
    @DisplayName("Quy tắc sở hữu")
    class QuyTacSoHuu {

        @Test
        @DisplayName("một thú cưng nhận được NHIỀU CO_OWNER")
        void nhieu_co_owner() {
            Integer petId = createPet(owner, null);

            petService.addOwner(petId, coOwner, PetOwnershipType.CO_OWNER);
            petService.addOwner(petId, secondCoOwner, PetOwnershipType.CO_OWNER);

            assertThat(inTransaction(() -> petOwnershipRepository.findAllByPetId(petId))).hasSize(3);
            assertThat(petService.ownerIdsOf(petId)).contains(owner, coOwner, secondCoOwner);
        }

        @Test
        @DisplayName("một thú cưng KHÔNG nhận được PRIMARY_OWNER thứ hai")
        void chi_mot_primary_owner() {
            Integer petId = createPet(owner, null);

            assertThatThrownBy(() -> petService.addOwner(petId, stranger, PetOwnershipType.PRIMARY_OWNER))
                    .isInstanceOf(ConflictException.class);

            long primaries = inTransaction(() -> petOwnershipRepository.findAllByPetId(petId)).stream()
                    .filter(ownership -> ownership.getOwnershipType() == PetOwnershipType.PRIMARY_OWNER)
                    .count();
            assertThat(primaries).isEqualTo(1);
        }

        @Test
        @DisplayName("cùng một người không giữ hai vai trò trên một thú cưng")
        void khong_giu_hai_vai_tro() {
            Integer petId = createPet(owner, null);

            assertThatThrownBy(() -> petService.addOwner(petId, owner, PetOwnershipType.CO_OWNER))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("chủ sở hữu xem được hồ sơ của mình ở mọi visibility")
        void chu_so_huu_xem_duoc_moi_visibility() {
            for (PetVisibility visibility : PetVisibility.values()) {
                Integer petId = createPet(owner, visibility.name());
                assertThat(petService.getOneById(petId, owner)).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("Đọc hồ sơ và quyền xem")
    class DocHoSo {

        @Test
        @DisplayName("hồ sơ đang tồn tại đọc được")
        void doc_duoc_ho_so() {
            Integer petId = createPet(owner, PetVisibility.PUBLIC.name());

            PetDtos.PetDetail detail = petService.getOneById(petId, owner);
            assertThat(detail.petId()).isEqualTo(petId);
            assertThat(detail.primaryOwnerId()).isEqualTo(owner);
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
        @DisplayName("CO_OWNER xem được hồ sơ PRIVATE mình đồng sở hữu")
        void co_owner_xem_duoc_private() {
            Integer petId = createPet(owner, PetVisibility.PRIVATE.name());
            petService.addOwner(petId, coOwner, PetOwnershipType.CO_OWNER);

            assertThat(petService.getOneById(petId, coOwner)).isNotNull();
        }

        @Test
        @DisplayName("danh sách của tôi gồm cả con mình chỉ là CO_OWNER, kèm đúng vai trò")
        void danh_sach_cua_toi() {
            Integer mine = createPet(owner, null);
            Integer shared = createPet(stranger, null);
            petService.addOwner(shared, owner, PetOwnershipType.CO_OWNER);

            List<PetDtos.PetListItem> pets = petService.getOwnedBy(owner);

            assertThat(pets).anyMatch(pet -> pet.petId().equals(mine)
                    && pet.myOwnershipType() == PetOwnershipType.PRIMARY_OWNER);
            assertThat(pets).anyMatch(pet -> pet.petId().equals(shared)
                    && pet.myOwnershipType() == PetOwnershipType.CO_OWNER);
        }

        @Test
        @DisplayName("danh sách của tôi không chứa hồ sơ của người khác")
        void danh_sach_khong_lan_cua_nguoi_khac() {
            Integer cuaNguoiLa = createPet(stranger, PetVisibility.PUBLIC.name());

            assertThat(petService.getOwnedBy(owner))
                    .noneMatch(pet -> pet.petId().equals(cuaNguoiLa));
        }
    }

    @Nested
    @DisplayName("Sửa hồ sơ")
    class SuaHoSo {

        @Test
        @DisplayName("chủ sở hữu sửa được các trường cho phép")
        void chu_so_huu_sua_duoc() {
            Integer petId = createPet(owner, null);

            PetDtos.PetDetail updated = petService.update(petId, owner,
                    updateRequest("Milo mới", PetVisibility.PRIVATE.name()));

            assertThat(updated.name()).isEqualTo("Milo mới");
            assertThat(updated.species()).isEqualTo(com.nguyenvu.lopet.pet.entity.PetSpecies.CAT);
            assertThat(updated.gender()).isEqualTo(com.nguyenvu.lopet.pet.entity.PetGender.FEMALE);
            assertThat(updated.breed()).isEqualTo("Munchkin");
            assertThat(updated.visibility()).isEqualTo(PetVisibility.PRIVATE);
        }

        @Test
        @DisplayName("CO_OWNER cũng sửa được")
        void co_owner_sua_duoc() {
            Integer petId = createPet(owner, null);
            petService.addOwner(petId, coOwner, PetOwnershipType.CO_OWNER);

            assertThat(petService.update(petId, coOwner, updateRequest("Do co-owner sửa", null)).name())
                    .isEqualTo("Do co-owner sửa");
        }

        @Test
        @DisplayName("người lạ KHÔNG sửa được dù biết petId")
        void nguoi_la_khong_sua_duoc() {
            Integer petId = createPet(owner, PetVisibility.PUBLIC.name());

            assertThatThrownBy(() -> petService.update(petId, stranger, updateRequest("cướp", null)))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("sửa hồ sơ không đụng tới status, người sở hữu chính hay createdAt")
        void sua_khong_dung_truong_domain() {
            Integer petId = createPet(owner, null);
            PetDtos.PetDetail before = petService.getOneById(petId, owner);

            PetDtos.PetDetail after = petService.update(petId, owner, updateRequest("Đổi tên", null));

            assertThat(after.status()).isEqualTo(PetStatus.ACTIVE);
            assertThat(after.primaryOwnerId()).isEqualTo(owner);
            assertThat(after.createdAt()).isEqualTo(before.createdAt());
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
    @DisplayName("Lưu trữ hồ sơ")
    class LuuTruHoSo {

        @Test
        @DisplayName("PRIMARY_OWNER lưu trữ được, và đó là xoá MỀM")
        void primary_owner_luu_tru_duoc() {
            Integer petId = createPet(owner, null);

            PetDtos.ArchivePetResponse response = petService.archive(petId, owner);
            assertThat(response.status()).isEqualTo(PetStatus.ARCHIVED);

            // Hàng vẫn còn trong bảng — chỉ bị @SQLRestriction che đi, không bị DELETE
            assertThat(archivedRowCount(petId)).isEqualTo(1L);
        }

        @Test
        @DisplayName("người lạ KHÔNG lưu trữ được")
        void nguoi_la_khong_luu_tru_duoc() {
            Integer petId = createPet(owner, PetVisibility.PUBLIC.name());

            assertThatThrownBy(() -> petService.archive(petId, stranger))
                    .isInstanceOf(ForbiddenException.class);
            assertThat(inTransaction(() -> petRepository.findById(petId))).isPresent();
        }

        @Test
        @DisplayName("CO_OWNER sửa được nhưng KHÔNG lưu trữ được")
        void co_owner_khong_luu_tru_duoc() {
            Integer petId = createPet(owner, null);
            petService.addOwner(petId, coOwner, PetOwnershipType.CO_OWNER);

            assertThatThrownBy(() -> petService.archive(petId, coOwner))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("guard chặn người lạ trước khi vào luồng lưu trữ")
        void guard_chan_luu_tru() {
            Integer petId = createPet(owner, PetVisibility.PUBLIC.name());

            authenticate(stranger);
            assertThatThrownBy(() -> petAccessGuard.requireOwnerToArchive(petId))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("hồ sơ đã lưu trữ biến mất khỏi MỌI luồng đọc")
        void da_luu_tru_thi_bien_mat() {
            Integer petId = createPet(owner, PetVisibility.PUBLIC.name());
            petService.archive(petId, owner);

            assertThatThrownBy(() -> petService.getOneById(petId, owner))
                    .isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> petService.getOneById(petId, null))
                    .isInstanceOf(NotFoundException.class);
            assertThat(petService.getOwnedBy(owner)).noneMatch(pet -> pet.petId().equals(petId));
            assertThat(inTransaction(() -> petRepository.findById(petId))).isEmpty();
        }

        @Test
        @DisplayName("hồ sơ đã lưu trữ không sửa được nữa")
        void da_luu_tru_thi_khong_sua_duoc() {
            Integer petId = createPet(owner, null);
            petService.archive(petId, owner);

            assertThatThrownBy(() -> petService.update(petId, owner, updateRequest("hồi sinh", null)))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("lưu trữ hai lần không được — lần hai hồ sơ đã biến mất")
        void luu_tru_hai_lan() {
            Integer petId = createPet(owner, null);
            petService.archive(petId, owner);

            assertThatThrownBy(() -> petService.archive(petId, owner))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    /**
     * Đếm bằng SQL THÔ: mọi truy vấn qua JPA đều bị {@code @SQLRestriction} lọc mất hàng đã xoá mềm,
     * nên không có cách nào khác để phân biệt "xoá mềm" với "xoá cứng" từ phía test.
     */
    private Long archivedRowCount(Integer petId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from pets where id = ? and deletedAt is not null and status = 'ARCHIVED'",
                Long.class, petId);
    }
}
