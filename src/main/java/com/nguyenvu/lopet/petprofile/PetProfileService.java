package com.nguyenvu.lopet.petprofile;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.common.exception.ConflictException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.pet.entity.Pet;
import com.nguyenvu.lopet.petprofile.dto.PetProfileDtos;
import com.nguyenvu.lopet.petprofile.entity.PetProfile;
import com.nguyenvu.lopet.petprofile.entity.PetProfileStatus;
import com.nguyenvu.lopet.petprofile.entity.PetVisibility;
import com.nguyenvu.lopet.petprofile.repository.PetProfileRepository;

import lombok.RequiredArgsConstructor;

/**
 * Hồ sơ công khai của thú cưng.
 *
 * <p>KHÔNG có hàm {@code create} public: hồ sơ chỉ ra đời qua {@link #createFor}, và hàm đó chỉ được
 * gọi từ {@code PetService.create} bên trong transaction tạo thú cưng. Mở một đường tạo hồ sơ rời sẽ
 * đẻ lại đúng lỗ hổng mà module hồ sơ chủ tài khoản đã phải refactor để bịt: hồ sơ mồ côi, và hồ sơ
 * gắn nhầm chủ.
 *
 * <p>{@code viewerId} của luồng đọc có thể {@code null} = khách chưa đăng nhập, và phải được truyền
 * xuống repository để lọc quyền xem NGAY TRONG câu truy vấn.
 */
@Service
@RequiredArgsConstructor
public class PetProfileService {

    private final PetProfileRepository petProfileRepository;

    /**
     * Dựng hồ sơ cho một thú cưng vừa được lưu. KHÔNG tự mở transaction —
     * {@code @Transactional(MANDATORY)} là ý đúng nhất nhưng repo chưa dùng propagation ở đâu cả,
     * nên bất biến "cùng transaction" được giữ bằng việc chỉ có một nơi gọi: {@code PetService.create}
     * đã mang {@code @Transactional}.
     *
     * @param pet thú cưng ĐÃ được lưu — hàm này cần {@code pet.getId()} để sinh handle
     */
    public PetProfile createFor(Pet pet, PetVisibility visibility) {
        return petProfileRepository.save(PetProfileFactory.seedFor(pet, visibility));
    }

    /**
     * Trả 404 (không phải 403) khi người xem không đủ quyền: phản hồi phải giống hệt trường hợp hồ
     * sơ không tồn tại, nếu không endpoint này thành công cụ dò xem một handle có tồn tại hay không.
     */
    @Transactional(readOnly = true)
    public PetProfileDtos.PublicPetProfile getByHandle(String handle, Integer viewerId) {
        PetProfile profile = petProfileRepository
                .findVisibleByHandle(PetProfilePolicy.requireHandle(handle), viewerId)
                .orElseThrow(NotFoundException::new);
        return PetProfileMapper.toPublic(profile);
    }

    @Transactional(readOnly = true)
    public PetProfileDtos.PublicPetProfile getByPetId(Integer petId, Integer viewerId) {
        PetProfile profile = petProfileRepository.findVisibleByPetId(petId, viewerId)
                .orElseThrow(NotFoundException::new);
        return PetProfileMapper.toPublic(profile);
    }

    /**
     * Sửa hồ sơ. Quyền sở hữu đã được {@code PetAccessGuard} kiểm ở controller — ở đây chỉ còn quy
     * tắc dữ liệu.
     *
     * <p>Ngữ nghĩa REPLACE cho các trường VĂN BẢN: {@code handle} và {@code displayName} là bắt
     * buộc, {@code bio} nhận chuỗi rỗng để xoá, {@code visibility} bỏ trống thì về PUBLIC. Client
     * luôn gửi đủ cả sáu trường nên không có gì bị mất ngoài ý muốn.
     *
     * <p><b>Riêng hai trường ẢNH thì MERGE</b>: {@code null} = giữ nguyên, chuỗi rỗng = xoá ảnh.
     * Đây là điều kiện để biến thể multipart hoạt động — request không đính file thì
     * {@code PetProfileController.uploadOrNull} trả null, và mỗi lần sửa bio mà coi null là "xoá" sẽ
     * thổi bay avatar. Đúng quy ước mà {@code AccountProfileService.updateMine} đang dùng.
     */
    @Transactional
    public PetProfileDtos.OwnedPetProfile update(Integer petId, PetProfileDtos.UpdatePetProfileRequest request) {
        PetProfile profile = petProfileRepository.findByPetId(petId).orElseThrow(NotFoundException::new);

        String handle = PetProfilePolicy.requireHandle(request.handle());
        // Chỉ kiểm khi handle thật sự đổi: so với chính mình thì lúc nào cũng đếm ra một hàng.
        if (!handle.equals(profile.getHandle())
                && petProfileRepository.countByHandleIncludingDeactivated(handle) > 0) {
            throw new ConflictException("Handle \"" + handle + "\" đã có người dùng");
        }

        profile.setHandle(handle);
        profile.setDisplayName(PetProfilePolicy.requireDisplayName(request.displayName()));
        profile.setBio(request.bio() == null ? "" : request.bio().trim());
        if (request.avatarUrl() != null) {
            profile.setAvatarUrl(request.avatarUrl().trim());
        }
        if (request.coverUrl() != null) {
            profile.setCoverUrl(request.coverUrl().trim());
        }
        profile.setVisibility(PetProfilePolicy.parseVisibility(request.visibility()));

        return PetProfileMapper.toOwned(petProfileRepository.save(profile));
    }

    /** Hồ sơ của chính chủ — không đi qua bộ lọc quyền xem vì guard đã xác nhận sở hữu */
    @Transactional(readOnly = true)
    public PetProfileDtos.OwnedPetProfile getOwned(Integer petId) {
        return PetProfileMapper.toOwned(
                petProfileRepository.findByPetId(petId).orElseThrow(NotFoundException::new));
    }

    /**
     * Tắt hồ sơ theo con vật. Gọi từ {@code PetService.deactivate} trong cùng transaction — hai bảng
     * phải cùng chuyển trạng thái, một con vật đã ngừng hoạt động mà hồ sơ vẫn hiện ra ở
     * {@code GET /v1/pet-profiles/handle/{handle}} là rò rỉ.
     *
     * <p>Ghi cả hai cột là có chủ đích: {@code deletedAt} là thứ khiến hồ sơ biến mất khỏi mọi truy
     * vấn (qua {@code @SQLRestriction}, không endpoint nào đi vòng qua được), còn {@code status} giữ
     * cho bản ghi tự mô tả được trạng thái khi đọc thẳng trong DB.
     */
    public void deactivateFor(Integer petId) {
        petProfileRepository.findByPetId(petId).ifPresent(profile -> {
            profile.setStatus(PetProfileStatus.DEACTIVATED);
            profile.setDeletedAt(LocalDateTime.now());
            petProfileRepository.save(profile);
        });
    }
}
