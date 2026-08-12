package com.nguyenvu.lopet.pet.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nguyenvu.lopet.pet.entity.PetOwnership;
import com.nguyenvu.lopet.pet.entity.PetOwnershipId;
import com.nguyenvu.lopet.pet.entity.PetOwnershipType;

/**
 * Chỉ bốn hàm, đúng bằng nhu cầu của các use case hiện có: kiểm tra sở hữu, chặn PRIMARY_OWNER thứ
 * hai, liệt kê chủ sở hữu cho guard, và nạp vai trò của người gọi cho danh sách "thú cưng của tôi".
 */
public interface PetOwnershipRepository extends JpaRepository<PetOwnership, PetOwnershipId> {

    /** Điều kiện chặn của quy tắc "mỗi thú cưng chỉ có một PRIMARY_OWNER" */
    boolean existsByPetIdAndOwnershipType(Integer petId, PetOwnershipType ownershipType);

    /** Tập chủ sở hữu hợp lệ để OwnershipGuard so với danh tính trong token */
    List<PetOwnership> findAllByPetId(Integer petId);

    Optional<PetOwnership> findByPetIdAndUserId(Integer petId, Integer userId);

    List<PetOwnership> findAllByUserId(Integer userId);
}
