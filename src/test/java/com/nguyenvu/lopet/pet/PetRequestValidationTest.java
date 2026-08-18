package com.nguyenvu.lopet.pet;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nguyenvu.lopet.pet.dto.PetDtos;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Ràng buộc Bean Validation trên request DTO — chạy thẳng bằng {@link Validator}, không cần Spring
 * context: đây đúng là tầng mà {@code @Valid} ở controller kích hoạt, và
 * {@code GlobalExceptionHandler.handleValidation} biến thành 400 "Validation error".
 *
 * <p>Giá trị enum sai KHÔNG được kiểm ở đây mà ở {@link PetManagementIntegrationTest}: ba trường đó
 * khai kiểu String nên qua được Bean Validation, việc chặn nằm ở {@link PetPolicy}.
 */
@DisplayName("Ràng buộc validation của request Pet")
class PetRequestValidationTest {

    private static final Validator VALIDATOR;

    static {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            VALIDATOR = factory.getValidator();
        }
    }

    private PetDtos.CreatePetRequest request(String name, String species, String breed, String gender,
                                              LocalDate dateOfBirth) {
        return new PetDtos.CreatePetRequest(name, species, breed, gender, dateOfBirth, null);
    }

    private PetDtos.CreatePetRequest valid() {
        return request("Milo", "DOG", "Golden Retriever", "MALE", LocalDate.of(2023, 3, 12));
    }

    private Set<String> fieldsInError(PetDtos.CreatePetRequest request) {
        return VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    @DisplayName("request hợp lệ không sinh lỗi nào")
    void request_hop_le() {
        assertThat(VALIDATOR.validate(valid())).isEmpty();
    }

    @Test
    @DisplayName("name rỗng / toàn khoảng trắng / null đều bị từ chối")
    void name_bat_buoc() {
        assertThat(fieldsInError(request(null, "DOG", null, "MALE", LocalDate.now()))).contains("name");
        assertThat(fieldsInError(request("", "DOG", null, "MALE", LocalDate.now()))).contains("name");
        assertThat(fieldsInError(request("   ", "DOG", null, "MALE", LocalDate.now()))).contains("name");
    }

    @Test
    @DisplayName("name quá 50 ký tự bị từ chối")
    void name_toi_da_50() {
        assertThat(fieldsInError(request("x".repeat(51), "DOG", null, "MALE", LocalDate.now())))
                .contains("name");
        assertThat(fieldsInError(request("x".repeat(50), "DOG", null, "MALE", LocalDate.now())))
                .doesNotContain("name");
    }

    @Test
    @DisplayName("species và gender là bắt buộc")
    void species_va_gender_bat_buoc() {
        assertThat(fieldsInError(request("Milo", null, null, null, LocalDate.now())))
                .contains("species", "gender");
    }

    @Test
    @DisplayName("breed quá 100 ký tự bị từ chối, bỏ trống thì không")
    void breed_toi_da_100() {
        assertThat(fieldsInError(request("Milo", "DOG", "x".repeat(101), "MALE", LocalDate.now())))
                .contains("breed");
        assertThat(fieldsInError(request("Milo", "DOG", null, "MALE", LocalDate.now())))
                .doesNotContain("breed");
    }

    @Test
    @DisplayName("dateOfBirth ở tương lai bị từ chối; hôm nay thì được")
    void ngay_sinh_khong_o_tuong_lai() {
        assertThat(fieldsInError(request("Milo", "DOG", null, "MALE", LocalDate.now().plusDays(1))))
                .contains("dateOfBirth");
        assertThat(fieldsInError(request("Milo", "DOG", null, "MALE", LocalDate.now())))
                .doesNotContain("dateOfBirth");
    }

    @Test
    @DisplayName("dateOfBirth bắt buộc — cột date_of_birth đang NOT NULL")
    void ngay_sinh_bat_buoc() {
        assertThat(fieldsInError(request("Milo", "DOG", null, "MALE", null))).contains("dateOfBirth");
    }

    @Test
    @DisplayName("UpdatePetRequest không có trường nào do domain quản lý")
    void update_khong_co_truong_cam() {
        Set<String> components = java.util.Arrays.stream(PetDtos.UpdatePetRequest.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .collect(java.util.stream.Collectors.toSet());

        // Client không có ĐƯỜNG NÀO gửi các trường này lên: chúng không tồn tại trong DTO, nên
        // không phụ thuộc vào việc service có nhớ bỏ qua chúng hay không.
        assertThat(components).doesNotContain("id", "petId", "ownerId", "userId", "ownershipType",
                "status", "deletedAt", "createdAt", "updatedAt");
        // bio/visibility cũng vắng mặt: chúng thuộc HỒ SƠ CÔNG KHAI và chỉ sửa được qua
        // PUT /v1/pet-profiles/{petId} — hai đường ghi vào cùng một cột là chỗ dữ liệu bắt đầu lệch.
        assertThat(components)
                .containsExactlyInAnyOrder("name", "species", "breed", "gender", "dateOfBirth");
    }

    @Test
    @DisplayName("CreatePetRequest cũng không có trường sở hữu")
    void create_khong_co_truong_so_huu() {
        Set<String> components = java.util.Arrays.stream(PetDtos.CreatePetRequest.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(components).doesNotContain("id", "petId", "ownerId", "userId", "ownershipType",
                "status", "deletedAt", "createdAt", "updatedAt");
    }

    @Test
    @DisplayName("thông điệp lỗi giữ đúng phong cách Joi của backend cũ")
    void thong_diep_giu_phong_cach_cu() {
        Set<ConstraintViolation<PetDtos.CreatePetRequest>> violations =
                VALIDATOR.validate(request(null, "DOG", null, "MALE", LocalDate.now()));

        assertThat(violations).anyMatch(violation -> "\"name\" is required".equals(violation.getMessage()));
    }
}
