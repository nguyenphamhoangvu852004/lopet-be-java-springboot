package com.nguyenvu.lopet.petprofile;

import com.nguyenvu.lopet.pet.entity.Pet;
import com.nguyenvu.lopet.petprofile.entity.PetProfile;
import com.nguyenvu.lopet.petprofile.entity.PetProfileStatus;
import com.nguyenvu.lopet.petprofile.entity.PetVisibility;

/**
 * Nguồn duy nhất sinh row {@code pet_profiles}, đối xứng với
 * {@link com.nguyenvu.lopet.accountprofile.AccountProfileFactory} của hồ sơ chủ tài khoản.
 *
 * <p>Người dùng KHÔNG tự tạo hồ sơ thú cưng: mỗi con vật được cấp sẵn một hồ sơ trong CÙNG transaction
 * với chính nó ({@code PetService.create}). Trạng thái "Pet tồn tại nhưng chưa có PetProfile" không
 * có nghĩa gì cả — con vật đó sẽ không xuất hiện ở bất kỳ luồng đọc nào, vì mọi truy vấn đều join
 * hồ sơ để lấy phạm vi riêng tư.
 *
 * <p>{@code handle} phải sinh SAU khi {@code pet.id} có giá trị, nên hàm này chỉ gọi được sau
 * {@code petRepository.save()} — xem {@link PetProfilePolicy#seedHandle}.
 */
public final class PetProfileFactory {

    /**
     * {@code displayName} lấy theo {@code pet.name} thay vì một cái tên bịa: đây là giá trị người
     * dùng vừa nhập, và họ đổi lại được ngay bằng {@code PUT /v1/pet-profiles/{petId}}.
     *
     * <p>{@code avatarUrl}/{@code coverUrl}/{@code bio} để rỗng chứ không null — client dựa vào việc
     * các khoá này luôn tồn tại, đúng quy ước {@code AccountProfileFactory} đang dùng.
     *
     * @param visibility phạm vi riêng tư người dùng chọn lúc tạo thú cưng; đây là trường DUY NHẤT
     *                   của hồ sơ mà luồng tạo pet nhận từ client
     */
    public static PetProfile seedFor(Pet pet, PetVisibility visibility) {
        return PetProfile.builder()
                .pet(pet)
                .handle(PetProfilePolicy.seedHandle(pet.getId()))
                .displayName(pet.getName())
                .avatarUrl("")
                .coverUrl("")
                .bio("")
                .status(PetProfileStatus.ACTIVE)
                .visibility(visibility)
                .build();
    }

    private PetProfileFactory() {
    }
}
