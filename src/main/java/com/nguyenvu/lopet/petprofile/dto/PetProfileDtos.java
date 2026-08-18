package com.nguyenvu.lopet.petprofile.dto;

import java.time.LocalDateTime;

import com.nguyenvu.lopet.petprofile.entity.PetProfileStatus;
import com.nguyenvu.lopet.petprofile.entity.PetVisibility;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code visibility} khai kiểu {@code String} trong request chứ không phải kiểu enum, cùng lý do
 * với {@code PetDtos}: Jackson gặp giá trị enum lạ ném {@code HttpMessageNotReadableException} mà
 * handler trung tâm không có nhánh cho nó, nên client nhận 500 thay vì 400.
 */
public final class PetProfileDtos {

    /**
     * KHÔNG có {@code petId}: hồ sơ được xác định bằng path variable, và quyền sở hữu đã kiểm ở
     * guard. Cũng KHÔNG có {@code status} — ngừng hoạt động là việc của
     * {@code DELETE /v1/pets/{petId}}, không phải hệ quả phụ của việc sửa bio.
     */
    public record UpdatePetProfileRequest(

            @NotNull(message = "\"handle\" is required")
            @NotBlank(message = "\"handle\" is not allowed to be empty")
            @Size(max = 30, message = "\"handle\" length must be less than or equal to 30 characters long")
            String handle,

            @NotNull(message = "\"displayName\" is required")
            @NotBlank(message = "\"displayName\" is not allowed to be empty")
            @Size(max = 50, message = "\"displayName\" length must be less than or equal to 50 characters long")
            String displayName,

            @Size(max = 500, message = "\"bio\" length must be less than or equal to 500 characters long")
            String bio,

            /**
             * {@code null} = GIỮ NGUYÊN ảnh hiện có, chuỗi rỗng = XOÁ ảnh. Biến thể multipart của
             * endpoint tự điền trường này bằng URL Cloudinary sau khi upload file {@code avatar}.
             */
            String avatarUrl,

            /** Cùng quy ước với {@code avatarUrl}; file tương ứng ở multipart tên là {@code cover} */
            String coverUrl,

            /** Bỏ trống thì mặc định PUBLIC */
            String visibility) {
    }

    /**
     * Hồ sơ như người NGOÀI nhìn thấy. Cố ý không có {@code ownerAccountId}: mạng xã hội này lấy pet
     * làm thực thể hoạt động, để lộ tài khoản người đứng sau là đi ngược đúng ranh giới mà refactor
     * này dựng lên.
     */
    public record PublicPetProfile(
            Integer petId,
            String handle,
            String displayName,
            String avatarUrl,
            String coverUrl,
            String bio,
            PetVisibility visibility,
            LocalDateTime createdAt) {
    }

    /** Hồ sơ như CHÍNH CHỦ nhìn thấy — thêm trạng thái và mốc cập nhật */
    public record OwnedPetProfile(
            Integer petId,
            String handle,
            String displayName,
            String avatarUrl,
            String coverUrl,
            String bio,
            PetProfileStatus status,
            PetVisibility visibility,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    private PetProfileDtos() {
    }
}
