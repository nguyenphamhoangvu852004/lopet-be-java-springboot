package com.nguyenvu.lopet.petprofile;

import com.nguyenvu.lopet.petprofile.dto.PetProfileDtos;
import com.nguyenvu.lopet.petprofile.entity.PetProfile;

/** Entity -> DTO. Không có chiều ngược lại: dựng entity là việc của service, nơi có đủ ngữ cảnh. */
public final class PetProfileMapper {

    public static PetProfileDtos.PublicPetProfile toPublic(PetProfile profile) {
        return new PetProfileDtos.PublicPetProfile(
                profile.getPet().getId(),
                profile.getHandle(),
                profile.getDisplayName(),
                profile.getAvatarUrl(),
                profile.getCoverUrl(),
                profile.getBio(),
                profile.getVisibility(),
                profile.getCreatedAt());
    }

    public static PetProfileDtos.OwnedPetProfile toOwned(PetProfile profile) {
        return new PetProfileDtos.OwnedPetProfile(
                profile.getPet().getId(),
                profile.getHandle(),
                profile.getDisplayName(),
                profile.getAvatarUrl(),
                profile.getCoverUrl(),
                profile.getBio(),
                profile.getStatus(),
                profile.getVisibility(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }

    private PetProfileMapper() {
    }
}
