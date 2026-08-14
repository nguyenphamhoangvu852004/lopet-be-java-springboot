package com.nguyenvu.lopet.pet.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.nguyenvu.lopet.pet.entity.PetGender;
import com.nguyenvu.lopet.pet.entity.PetOwnershipType;
import com.nguyenvu.lopet.pet.entity.PetSpecies;
import com.nguyenvu.lopet.pet.entity.PetStatus;
import com.nguyenvu.lopet.pet.entity.PetVisibility;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * Ba trường enum ({@code species}, {@code gender}, {@code visibility}) khai kiểu {@code String}
 * trong request chứ không phải kiểu enum. Lý do là hành vi lỗi: Jackson gặp giá trị enum lạ sẽ ném
 * {@code HttpMessageNotReadableException}, mà handler trung tâm không có nhánh cho ngoại lệ đó nên
 * client nhận 500 thay vì 400. Nhận String rồi để {@code PetPolicy} chuyển đổi cho ra 400 kèm danh
 * sách giá trị hợp lệ — đúng cách {@code PostController} nhận {@code scope} dạng String.
 *
 * <p>Response thì ngược lại, dùng thẳng enum: chiều ra không có rủi ro parse.
 */
public final class PetDtos {

    public record CreatePetRequest(

            @NotNull(message = "\"name\" is required")
            @NotBlank(message = "\"name\" is not allowed to be empty")
            @Size(max = 50, message = "\"name\" length must be less than or equal to 50 characters long")
            String name,

            @NotNull(message = "\"species\" is required")
            @NotBlank(message = "\"species\" is not allowed to be empty")
            String species,

            @Size(max = 100, message = "\"breed\" length must be less than or equal to 100 characters long")
            String breed,

            @NotNull(message = "\"gender\" is required")
            @NotBlank(message = "\"gender\" is not allowed to be empty")
            String gender,

            /**
             * Bắt buộc vì cột {@code pets.date_of_birth} là NOT NULL — xem ghi chú lệch thiết kế ở
             * {@link com.nguyenvu.lopet.pet.PetPolicy#requireDateOfBirth}.
             */
            @NotNull(message = "\"dateOfBirth\" is required")
            @PastOrPresent(message = "\"dateOfBirth\" must be less than or equal to today")
            LocalDate dateOfBirth,

            @Size(max = 500, message = "\"bio\" length must be less than or equal to 500 characters long")
            String bio,

            /** Bỏ trống thì mặc định PUBLIC */
            String visibility) {
    }

    /**
     * Cùng bộ trường với create, KHÔNG có {@code status}/{@code deletedAt}/{@code createdAt}/
     * {@code updatedAt} và cũng không có bất kỳ trường sở hữu nào: chuyển quyền sở hữu là một use
     * case riêng, không phải hệ quả phụ của việc sửa tên thú cưng.
     */
    public record UpdatePetRequest(

            @NotNull(message = "\"name\" is required")
            @NotBlank(message = "\"name\" is not allowed to be empty")
            @Size(max = 50, message = "\"name\" length must be less than or equal to 50 characters long")
            String name,

            @NotNull(message = "\"species\" is required")
            @NotBlank(message = "\"species\" is not allowed to be empty")
            String species,

            @Size(max = 100, message = "\"breed\" length must be less than or equal to 100 characters long")
            String breed,

            @NotNull(message = "\"gender\" is required")
            @NotBlank(message = "\"gender\" is not allowed to be empty")
            String gender,

            @NotNull(message = "\"dateOfBirth\" is required")
            @PastOrPresent(message = "\"dateOfBirth\" must be less than or equal to today")
            LocalDate dateOfBirth,

            @Size(max = 500, message = "\"bio\" length must be less than or equal to 500 characters long")
            String bio,

            String visibility) {
    }

    /** Chi tiết một hồ sơ — kèm chủ sở hữu chính để client biết ai được phép xoá */
    public record PetDetail(
            Integer petId,
            String name,
            PetSpecies species,
            String breed,
            PetGender gender,
            LocalDate dateOfBirth,
            String bio,
            PetStatus status,
            PetVisibility visibility,
            Integer primaryOwnerId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    /** Phần tử của "thú cưng của tôi" — {@code myOwnershipType} là vai trò của chính người gọi */
    public record PetListItem(
            Integer petId,
            String name,
            PetSpecies species,
            String breed,
            PetGender gender,
            LocalDate dateOfBirth,
            PetStatus status,
            PetVisibility visibility,
            PetOwnershipType myOwnershipType,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    /** Trả về trạng thái sau khi lưu trữ để client không phải đoán */
    public record ArchivePetResponse(Integer petId, PetStatus status) {
    }

    private PetDtos() {
    }
}
