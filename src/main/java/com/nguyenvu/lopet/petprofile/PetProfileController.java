package com.nguyenvu.lopet.petprofile;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.nguyenvu.lopet.common.media.CloudinaryService;
import com.nguyenvu.lopet.common.response.ApiResponse;
import com.nguyenvu.lopet.pet.PetAccessGuard;
import com.nguyenvu.lopet.petprofile.dto.PetProfileDtos;
import com.nguyenvu.lopet.security.Auth;
import com.nguyenvu.lopet.security.CurrentUser;
import com.nguyenvu.lopet.security.RequirePermission;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Chỉ làm ba việc: nhận HTTP, lấy danh tính từ token, gọi service. Không quy tắc nghiệp vụ nào.
 *
 * <p>Đây là module DUY NHẤT có endpoint đọc công khai trong toàn bộ nhánh Account/Pet. Hồ sơ chủ tài
 * khoản ({@code /v1/account-profiles}) chỉ chính chủ đọc được; người lạ trên mạng xã hội này nhìn
 * thấy pet, không nhìn thấy người.
 *
 * <p>KHÔNG có endpoint tạo: hồ sơ ra đời cùng thú cưng trong {@code POST /v1/pets}.
 */
@RestController
@RequestMapping("/v1/pet-profiles")
@RequiredArgsConstructor
public class PetProfileController {

    private final PetProfileService petProfileService;
    private final PetAccessGuard petAccessGuard;
    private final CloudinaryService cloudinaryService;

    /* ---------- Đọc: công khai, có lọc quyền xem ---------- */

    /**
     * {@code @Auth(required = false)}: hồ sơ PUBLIC vẫn xem được khi chưa đăng nhập, nhưng vẫn nhận
     * diện được người đã đăng nhập để mở đúng phần họ được xem. Việc lọc nằm ở tầng repository.
     *
     * <p>Đặt dưới {@code /handle/} chứ không phải {@code /{handle}} ở gốc: gốc đã có {@code /{petId}}
     * kiểu số, và hai path variable cùng cấp khác kiểu là chỗ Spring chọn nhầm handler khi ai đó đặt
     * handle toàn chữ số.
     */
    @GetMapping("/handle/{handle}")
    @Auth(required = false)
    public ApiResponse<PetProfileDtos.PublicPetProfile> getByHandle(@PathVariable String handle) {
        return ApiResponse.ok("Get pet profile successfully",
                petProfileService.getByHandle(handle, CurrentUser.viewerId()));
    }

    @GetMapping("/{petId}")
    @Auth(required = false)
    public ApiResponse<PetProfileDtos.PublicPetProfile> getByPetId(@PathVariable Integer petId) {
        return ApiResponse.ok("Get pet profile successfully",
                petProfileService.getByPetId(petId, CurrentUser.viewerId()));
    }

    /* ---------- Ghi: chỉ chủ sở hữu ---------- */

    @GetMapping("/{petId}/owned")
    @Auth
    public ApiResponse<PetProfileDtos.OwnedPetProfile> getOwned(@PathVariable Integer petId) {
        petAccessGuard.requireOwnerToEdit(petId);
        return ApiResponse.ok("Get pet profile successfully", petProfileService.getOwned(petId));
    }

    /**
     * Biến thể MULTIPART — đường chính để đổi ảnh, cùng luồng với
     * {@code PUT /v1/account-profiles}: file đi qua {@link CloudinaryService}, và URL trả về mới là
     * thứ được lưu xuống DB.
     *
     * <p>Trước đây module này chỉ có biến thể JSON, nên client buộc phải tự có sẵn một URL ảnh từ
     * đâu đó — trong khi luồng upload duy nhất của hệ thống nằm ở đây. Hệ quả là giao diện hồ sơ thú
     * cưng không đổi được avatar bằng file như hồ sơ tài khoản.
     *
     * <p>KHÔNG đính file nghĩa là GIỮ NGUYÊN ảnh cũ ({@link #uploadOrNull} trả null, và service bỏ
     * qua null) — đúng quy ước của hồ sơ tài khoản. Muốn XOÁ ảnh thì gửi tường minh chuỗi rỗng ở
     * trường {@code avatarUrl}/{@code coverUrl}.
     *
     * <p>Không dùng {@code @Valid} được ở đây vì DTO được dựng tay từ các form field; ràng buộc
     * tương đương do {@code PetProfilePolicy} thực thi và cũng cho ra 400.
     */
    @PutMapping(path = "/{petId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Auth
    @RequirePermission("petProfile:update:own")
    public ApiResponse<PetProfileDtos.OwnedPetProfile> updateMultipart(
            @PathVariable Integer petId,
            @RequestParam(required = false) String handle,
            @RequestParam(required = false) String displayName,
            @RequestParam(required = false) String bio,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) String avatarUrl,
            @RequestParam(required = false) String coverUrl,
            @RequestPart(name = "avatar", required = false) MultipartFile avatar,
            @RequestPart(name = "cover", required = false) MultipartFile cover) {
        petAccessGuard.requireOwnerToEdit(petId);

        // File thắng URL: đính file nghĩa là người dùng vừa chọn ảnh mới, còn avatarUrl trong cùng
        // request chỉ là giá trị cũ mà form gửi kèm.
        String resolvedAvatar = firstNonNull(uploadOrNull(avatar), avatarUrl);
        String resolvedCover = firstNonNull(uploadOrNull(cover), coverUrl);

        return ApiResponse.ok("Update pet profile successfully",
                petProfileService.update(petId, new PetProfileDtos.UpdatePetProfileRequest(
                        handle, displayName, bio, resolvedAvatar, resolvedCover, visibility)));
    }

    /** Biến thể JSON: không mang được file nên chỉ nhận URL có sẵn */
    @PutMapping(path = "/{petId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Auth
    @RequirePermission("petProfile:update:own")
    public ApiResponse<PetProfileDtos.OwnedPetProfile> update(
            @PathVariable Integer petId,
            @Valid @RequestBody PetProfileDtos.UpdatePetProfileRequest request) {
        petAccessGuard.requireOwnerToEdit(petId);
        return ApiResponse.ok("Update pet profile successfully", petProfileService.update(petId, request));
    }

    /** null (không phải chuỗi rỗng) khi không có file — đó là tín hiệu "giữ nguyên ảnh cũ" */
    private String uploadOrNull(MultipartFile file) {
        return file == null || file.isEmpty() ? null : cloudinaryService.uploadImage(file);
    }

    private String firstNonNull(String preferred, String fallback) {
        return preferred != null ? preferred : fallback;
    }
}
