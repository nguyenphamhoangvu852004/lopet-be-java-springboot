package com.nguyenvu.lopet.pet;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.pet.entity.Pet;
import com.nguyenvu.lopet.pet.repository.PetRepository;
import com.nguyenvu.lopet.security.CurrentUser;
import com.nguyenvu.lopet.security.authz.OwnershipGuard;

import lombok.RequiredArgsConstructor;

/**
 * Tầng ownership của module Pet — bản đối xứng của {@link com.nguyenvu.lopet.post.PostAccessGuard}.
 *
 * <p>Nạp hồ sơ bằng bản ĐÃ lọc quyền xem chứ không phải bản thô: guard trả 404 khi không nạp được
 * và 403 khi nạp được nhưng người gọi không phải chủ. Dùng bản không lọc thì hai lỗi đó phân biệt
 * được "thú cưng này tồn tại nhưng không phải của bạn" với "không có gì cả" — đủ để quét id và biết
 * hồ sơ PRIVATE nào đang tồn tại. Chủ sở hữu luôn tự xem được hồ sơ mình (nhánh B của
 * {@link com.nguyenvu.lopet.pet.repository.PetVisibilityFilter}) nên bộ lọc không chặn nhầm người
 * có quyền thật.
 *
 * <p>{@link OwnershipGuard#NO_BYPASS} cho cả sửa lẫn lưu trữ: danh mục quyền không có mã
 * {@code pet:delete} dành cho staff, nên chưa vai trò nào được đi vòng qua tầng này. Thêm bypass mà
 * không có permission tương ứng là mở quyền âm thầm.
 *
 * <p>Là bean riêng chứ không phải method trong controller: {@code @Transactional} chỉ có tác dụng
 * khi lời gọi đi qua proxy, gọi nội bộ trong cùng một lớp thì annotation bị bỏ qua.
 */
@Component
@RequiredArgsConstructor
public class PetAccessGuard {

    private final PetRepository petRepository;
    private final PetService petService;

    /** Sửa hồ sơ: PRIMARY_OWNER và CO_OWNER đều qua được */
    @Transactional(readOnly = true)
    public void requireOwnerToEdit(Integer petId) {
        check(petId);
    }

    /**
     * Lưu trữ hồ sơ: guard chỉ trả lời "có phải chủ sở hữu không". Ràng buộc chặt hơn — phải đúng
     * PRIMARY_OWNER — nằm ở {@link PetService#archive}, vì OwnershipGuard chỉ so khớp tập id chứ
     * không phân biệt vai trò.
     */
    @Transactional(readOnly = true)
    public void requireOwnerToArchive(Integer petId) {
        check(petId);
    }

    private void check(Integer petId) {
        Integer viewerId = CurrentUser.viewerId();
        OwnershipGuard.check(
                () -> petRepository.findVisibleById(petId, viewerId).orElse(null),
                (Pet pet) -> petService.ownerIdsOf(pet.getId()),
                OwnershipGuard.NO_BYPASS);
    }
}
