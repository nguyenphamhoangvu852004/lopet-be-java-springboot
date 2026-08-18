package com.nguyenvu.lopet.pet;

import com.nguyenvu.lopet.pet.dto.PetDtos;
import com.nguyenvu.lopet.pet.entity.Pet;
import com.nguyenvu.lopet.petprofile.entity.PetProfile;

/** Entity -> DTO. Không có chiều ngược lại: dựng entity là việc của service, nơi có đủ ngữ cảnh. */
public final class PetMapper {

    public static PetDtos.PetDetail toDetail(Pet pet) {
        PetProfile profile = pet.getPetProfile();
        return new PetDtos.PetDetail(
                pet.getId(),
                pet.getName(),
                pet.getSpecies(),
                pet.getBreed(),
                pet.getGender(),
                pet.getDateOfBirth(),
                pet.getStatus(),
                pet.getAccount().getId(),
                toProfileSummary(profile),
                pet.getCreatedAt(),
                pet.getUpdatedAt());
    }

    public static PetDtos.PetListItem toListItem(Pet pet) {
        return new PetDtos.PetListItem(
                pet.getId(),
                pet.getName(),
                pet.getSpecies(),
                pet.getBreed(),
                pet.getGender(),
                pet.getDateOfBirth(),
                pet.getStatus(),
                toProfileSummary(pet.getPetProfile()),
                pet.getCreatedAt(),
                pet.getUpdatedAt());
    }

    /**
     * {@code null} chỉ xảy ra với dữ liệu cũ chưa được backfill hồ sơ — luồng tạo mới không sinh ra
     * được (xem {@code PetService.create}). Vẫn chịu null để một hàng lỗi không làm vỡ cả danh sách.
     */
    private static PetDtos.PetProfileSummary toProfileSummary(PetProfile profile) {
        if (profile == null) {
            return null;
        }
        return new PetDtos.PetProfileSummary(profile.getHandle(), profile.getDisplayName(),
                profile.getAvatarUrl(), profile.getBio(), profile.getVisibility());
    }

    private PetMapper() {
    }
}
