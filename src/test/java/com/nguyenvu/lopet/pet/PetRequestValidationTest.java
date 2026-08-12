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
                                              LocalDate dateOfBirth, String bio) {
        return new PetDtos.CreatePetRequest(name, species, breed, gender, dateOfBirth, bio, null);
    }

    private PetDtos.CreatePetRequest valid() {
        return request("Milo", "DOG", "Golden Retriever", "MALE", LocalDate.of(2023, 3, 12), "Always hungry.");
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
        assertThat(fieldsInError(request(null, "DOG", null, "MALE", LocalDate.now(), null))).contains("name");
        assertThat(fieldsInError(request("", "DOG", null, "MALE", LocalDate.now(), null))).contains("name");
        assertThat(fieldsInError(request("   ", "DOG", null, "MALE", LocalDate.now(), null))).contains("name");
    }

    @Test
    @DisplayName("name quá 50 ký tự bị từ chối")
    void name_toi_da_50() {
        assertThat(fieldsInError(request("x".repeat(51), "DOG", null, "MALE", LocalDate.now(), null)))
                .contains("name");
        assertThat(fieldsInError(request("x".repeat(50), "DOG", null, "MALE", LocalDate.now(), null)))
                .doesNotContain("name");
    }

    @Test
    @DisplayName("species và gender là bắt buộc")
    void species_va_gender_bat_buoc() {
        assertThat(fieldsInError(request("Milo", null, null, null, LocalDate.now(), null)))
                .contains("species", "gender");
    }

    @Test
    @DisplayName("breed quá 100 ký tự bị từ chối, bỏ trống thì không")
    void breed_toi_da_100() {
        assertThat(fieldsInError(request("Milo", "DOG", "x".repeat(101), "MALE", LocalDate.now(), null)))
                .contains("breed");
        assertThat(fieldsInError(request("Milo", "DOG", null, "MALE", LocalDate.now(), null)))
                .doesNotContain("breed");
    }

    @Test
    @DisplayName("bio quá 500 ký tự bị từ chối, bỏ trống thì không")
    void bio_toi_da_500() {
        assertThat(fieldsInError(request("Milo", "DOG", null, "MALE", LocalDate.now(), "x".repeat(501))))
                .contains("bio");
        assertThat(fieldsInError(request("Milo", "DOG", null, "MALE", LocalDate.now(), null)))
                .doesNotContain("bio");
    }

    @Test
    @DisplayName("dateOfBirth ở tương lai bị từ chối; hôm nay thì được")
    void ngay_sinh_khong_o_tuong_lai() {
        assertThat(fieldsInError(request("Milo", "DOG", null, "MALE", LocalDate.now().plusDays(1), null)))
                .contains("dateOfBirth");
        assertThat(fieldsInError(request("Milo", "DOG", null, "MALE", LocalDate.now(), null)))
                .doesNotContain("dateOfBirth");
    }

    @Test
    @DisplayName("dateOfBirth bắt buộc — cột date_of_birth đang NOT NULL")
    void ngay_sinh_bat_buoc() {
        assertThat(fieldsInError(request("Milo", "DOG", null, "MALE", null, null))).contains("dateOfBirth");
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
        assertThat(components)
                .containsExactlyInAnyOrder("name", "species", "breed", "gender", "dateOfBirth", "bio", "visibility");
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
                VALIDATOR.validate(request(null, "DOG", null, "MALE", LocalDate.now(), null));

        assertThat(violations).anyMatch(violation -> "\"name\" is required".equals(violation.getMessage()));
    }
}
