package com.nguyenvu.lopet.petprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

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
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.ConflictException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.pet.PetService;
import com.nguyenvu.lopet.pet.dto.PetDtos;
import com.nguyenvu.lopet.petprofile.dto.PetProfileDtos;
import com.nguyenvu.lopet.petprofile.entity.PetVisibility;
import com.nguyenvu.lopet.support.IntegrationTestBase;

/**
 * Hồ sơ công khai của thú cưng — thực thể duy nhất trong nhánh Account/Pet còn đường đọc cho người lạ.
 *
 * <p>Trọng tâm là hai thứ mà mô hình mới dựng lên và không được phép hỏng: {@code handle} phải duy
 * nhất và ổn định làm khoá tra cứu công khai, và hồ sơ phải chịu đúng bộ lọc quyền xem mà module pet
 * đang dùng — hai module KHÔNG được có hai câu trả lời khác nhau cho cùng câu hỏi "ai xem được".
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Pet Profile")
class PetProfileIntegrationTest extends IntegrationTestBase {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> IntegrationTestBase.jdbcUrl("lopet_java_petprofile_test"));
    }

    @Autowired
    private PetProfileService petProfileService;
    @Autowired
    private PetService petService;
    @Autowired
    private AccountRepository accountRepository;

    private static final String RUN = Long.toString(System.nanoTime(), 36);

    private Integer owner;
    private Integer stranger;

    @BeforeAll
    void seed() {
        inTransaction(() -> {
            owner = account("pp-owner").getId();
            stranger = account("pp-stranger").getId();
        });
    }

    private Account account(String name) {
        String unique = name + "-" + RUN;
        return accountRepository.save(Account.builder()
                .email(unique + "@pp.local").username(unique).password("x").isBanned(0).build());
    }

    private Integer createPet(Integer ownerId, String name, String visibility) {
        return petService.create(ownerId, new PetDtos.CreatePetRequest(
                name, "DOG", null, "MALE", LocalDate.of(2023, 1, 1), visibility)).petId();
    }

    private PetProfileDtos.UpdatePetProfileRequest update(String handle, String displayName, String visibility) {
        return new PetProfileDtos.UpdatePetProfileRequest(handle, displayName, "Xin chào", "", "", visibility);
    }

    @Nested
    @DisplayName("Handle")
    class Handle {

        @Test
        @DisplayName("hồ sơ mới có handle sinh từ id, và tra được bằng chính handle đó")
        void handle_mac_dinh() {
            Integer petId = createPet(owner, "Milo", null);

            PetProfileDtos.PublicPetProfile profile =
                    petProfileService.getByHandle("pet_" + petId, null);
            assertThat(profile.petId()).isEqualTo(petId);
        }

        @Test
        @DisplayName("đổi được sang handle khác và tra bằng handle mới")
        void doi_duoc_handle() {
            Integer petId = createPet(owner, "Milo", null);
            String handle = "milo_" + System.nanoTime();

            petProfileService.update(petId, update(handle, "Milo Đẹp Trai", null));

            assertThat(petProfileService.getByHandle(handle, null).displayName()).isEqualTo("Milo Đẹp Trai");
        }

        @Test
        @DisplayName("handle được chuẩn hoá về chữ thường — MySQL không phân biệt hoa thường trên UNIQUE")
        void handle_chuan_hoa_chu_thuong() {
            Integer petId = createPet(owner, "Milo", null);
            String handle = "MiLo_" + System.nanoTime();

            PetProfileDtos.OwnedPetProfile updated = petProfileService.update(petId, update(handle, "Milo", null));

            assertThat(updated.handle()).isEqualTo(handle.toLowerCase());
        }

        @Test
        @DisplayName("handle trùng của người khác trả 409, không phải 500")
        void handle_trung_tra_409() {
            Integer first = createPet(owner, "Milo", null);
            Integer second = createPet(owner, "Luna", null);
            String handle = "trung_" + System.nanoTime();

            petProfileService.update(first, update(handle, "Milo", null));

            // 409 chứ không phải DataIntegrityViolationException -> 500: đây là trạng thái người
            // dùng sửa được, phải nói cho họ biết.
            assertThatThrownBy(() -> petProfileService.update(second, update(handle, "Luna", null)))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("giữ nguyên handle của chính mình thì KHÔNG bị báo trùng")
        void giu_handle_cua_minh() {
            Integer petId = createPet(owner, "Milo", null);
            String handle = "giunguyen_" + System.nanoTime();

            petProfileService.update(petId, update(handle, "Milo", null));
            petProfileService.update(petId, update(handle, "Milo đổi tên", null));

            assertThat(petProfileService.getByHandle(handle, null).displayName()).isEqualTo("Milo đổi tên");
        }

        @Test
        @DisplayName("ký tự không hợp lệ bị từ chối ở tầng service")
        void handle_sai_dinh_dang() {
            Integer petId = createPet(owner, "Milo", null);

            for (String invalid : new String[] {"a", "có dấu", "milo.dog", "milo-dog", "x".repeat(31)}) {
                assertThatThrownBy(() -> petProfileService.update(petId, update(invalid, "Milo", null)))
                        .isInstanceOf(BadRequestException.class);
            }
        }
    }

    @Nested
    @DisplayName("Quyền xem")
    class QuyenXem {

        @Test
        @DisplayName("PUBLIC: khách chưa đăng nhập đọc được")
        void public_khach_doc_duoc() {
            Integer petId = createPet(owner, "Milo", PetVisibility.PUBLIC.name());
            assertThat(petProfileService.getByPetId(petId, null)).isNotNull();
        }

        @Test
        @DisplayName("PRIVATE: người lạ và khách nhận 404, chủ vẫn đọc được")
        void private_an_voi_nguoi_la() {
            Integer petId = createPet(owner, "Milo", PetVisibility.PRIVATE.name());

            assertThatThrownBy(() -> petProfileService.getByPetId(petId, stranger))
                    .isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> petProfileService.getByPetId(petId, null))
                    .isInstanceOf(NotFoundException.class);
            assertThat(petProfileService.getByPetId(petId, owner)).isNotNull();
        }

        @Test
        @DisplayName("tra bằng handle chịu đúng bộ lọc như tra bằng petId")
        void handle_cung_bi_loc() {
            Integer petId = createPet(owner, "Milo", PetVisibility.PRIVATE.name());
            String handle = "rieng_" + System.nanoTime();
            petProfileService.update(petId, update(handle, "Milo", PetVisibility.PRIVATE.name()));

            assertThatThrownBy(() -> petProfileService.getByHandle(handle, stranger))
                    .isInstanceOf(NotFoundException.class);
            assertThat(petProfileService.getByHandle(handle, owner)).isNotNull();
        }

        @Test
        @DisplayName("đổi visibility sang PRIVATE thì hồ sơ ẩn ngay với người lạ")
        void doi_visibility_co_hieu_luc_ngay() {
            Integer petId = createPet(owner, "Milo", PetVisibility.PUBLIC.name());
            assertThat(petProfileService.getByPetId(petId, stranger)).isNotNull();

            petProfileService.update(petId, update("doiscope_" + System.nanoTime(), "Milo",
                    PetVisibility.PRIVATE.name()));

            assertThatThrownBy(() -> petProfileService.getByPetId(petId, stranger))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Nội dung hồ sơ")
    class NoiDung {

        @Test
        @DisplayName("hồ sơ công khai KHÔNG lộ tài khoản người đứng sau")
        void khong_lo_chu_so_huu() {
            Integer petId = createPet(owner, "Milo", null);

            // Ranh giới của toàn bộ refactor: người lạ nhìn thấy pet, không nhìn thấy người.
            assertThat(java.util.Arrays.stream(
                            PetProfileDtos.PublicPetProfile.class.getRecordComponents())
                    .map(java.lang.reflect.RecordComponent::getName))
                    .doesNotContain("ownerAccountId", "accountId", "ownerId");
            assertThat(petProfileService.getByPetId(petId, null)).isNotNull();
        }

        @Test
        @DisplayName("displayName rỗng bị từ chối")
        void display_name_bat_buoc() {
            Integer petId = createPet(owner, "Milo", null);

            assertThatThrownBy(() -> petProfileService.update(petId,
                    update("hople_" + System.nanoTime(), "   ", null)))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("visibility lạ bị từ chối kèm danh sách hợp lệ")
        void visibility_la_bi_tu_choi() {
            Integer petId = createPet(owner, "Milo", null);

            assertThatThrownBy(() -> petProfileService.update(petId,
                    update("hople_" + System.nanoTime(), "Milo", "EVERYONE")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("visibility");
        }
    }
}
