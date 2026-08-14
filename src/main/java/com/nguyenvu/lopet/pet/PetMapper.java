package com.nguyenvu.lopet.pet;

import com.nguyenvu.lopet.pet.dto.PetDtos;
import com.nguyenvu.lopet.pet.entity.Pet;
import com.nguyenvu.lopet.pet.entity.PetOwnershipType;

/** Entity -> DTO. Không có chiều ngược lại: dựng entity là việc của service, nơi có đủ ngữ cảnh. */
public final class PetMapper {

    public static PetDtos.PetDetail toDetail(Pet pet, Integer primaryOwnerId) {
        return new PetDtos.PetDetail(
                pet.getId(),
                pet.getName(),
                pet.getSpecies(),
                pet.getBreed(),
                pet.getGender(),
                pet.getDateOfBirth(),
                pet.getBio(),
                pet.getStatus(),
                pet.getVisibility(),
                primaryOwnerId,
                pet.getCreatedAt(),
                pet.getUpdatedAt());
    }

    public static PetDtos.PetListItem toListItem(Pet pet, PetOwnershipType myOwnershipType) {
        return new PetDtos.PetListItem(
                pet.getId(),
                pet.getName(),
                pet.getSpecies(),
                pet.getBreed(),
                pet.getGender(),
                pet.getDateOfBirth(),
                pet.getStatus(),
                pet.getVisibility(),
                myOwnershipType,
                pet.getCreatedAt(),
                pet.getUpdatedAt());
    }

    private PetMapper() {
    }
}
