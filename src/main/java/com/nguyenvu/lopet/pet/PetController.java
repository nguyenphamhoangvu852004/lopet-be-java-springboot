package com.nguyenvu.lopet.pet;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nguyenvu.lopet.common.response.ApiResponse;
import com.nguyenvu.lopet.pet.dto.PetDtos;
import com.nguyenvu.lopet.security.Auth;
import com.nguyenvu.lopet.security.CurrentUser;
import com.nguyenvu.lopet.security.RequirePermission;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Chỉ làm ba việc: nhận HTTP, lấy danh tính từ token, gọi service. Không quy tắc nghiệp vụ nào —
 * "ai được sửa hồ sơ nào", "một thú cưng có mấy chủ chính" đều nằm ở {@link PetService}.
 *
 * <p>Base path {@code /v1} (không có {@code /api}) theo đúng convention của
 * {@link com.nguyenvu.lopet.post.PostController} và toàn bộ controller còn lại.
 */
@RestController
@RequestMapping("/v1/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;
    private final PetAccessGuard petAccessGuard;

    /**
     * Client KHÔNG gửi được {@code ownerId} hay {@code ownershipType} — chúng không tồn tại trong
     * {@link PetDtos.CreatePetRequest}. Chủ sở hữu chính suy ra từ token, ngay tại dòng dưới đây.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Auth
    @RequirePermission("pet:create")
    public ApiResponse<PetDtos.PetDetail> create(@Valid @RequestBody PetDtos.CreatePetRequest request) {
        return ApiResponse.created("Create pet successfully",
                petService.create(CurrentUser.require().id(), request));
    }

    /**
     * Đặt TRƯỚC {@code /{petId}} cho dễ đọc; Spring vốn ưu tiên path cố định hơn path variable nên
     * thứ tự khai báo không phải là thứ quyết định.
     */
    @GetMapping("/me")
    @Auth
    public ApiResponse<List<PetDtos.PetListItem>> getMine() {
        return ApiResponse.ok("Get my pets successfully",
                petService.getOwnedBy(CurrentUser.require().id()));
    }

    /**
     * {@code @Auth(required = false)}: hồ sơ PUBLIC vẫn xem được khi chưa đăng nhập, nhưng vẫn nhận
     * diện được người đã đăng nhập để mở đúng phần họ được xem. Việc lọc nằm ở tầng repository.
     */
    @GetMapping("/{petId}")
    @Auth(required = false)
    public ApiResponse<PetDtos.PetDetail> getById(@PathVariable Integer petId) {
        return ApiResponse.ok("Get pet successfully", petService.getOneById(petId, CurrentUser.viewerId()));
    }

    @PutMapping("/{petId}")
    @Auth
    @RequirePermission("pet:update:own")
    public ApiResponse<PetDtos.PetDetail> update(@PathVariable Integer petId,
                                                  @Valid @RequestBody PetDtos.UpdatePetRequest request) {
        petAccessGuard.requireOwnerToEdit(petId);
        return ApiResponse.ok("Update pet successfully",
                petService.update(petId, CurrentUser.require().id(), request));
    }

    /** Xoá mềm: hồ sơ chuyển sang ARCHIVED và biến mất khỏi mọi luồng đọc, không xoá cứng */
    @DeleteMapping("/{petId}")
    @Auth
    @RequirePermission("pet:delete:own")
    public ApiResponse<PetDtos.ArchivePetResponse> archive(@PathVariable Integer petId) {
        petAccessGuard.requireOwnerToArchive(petId);
        return ApiResponse.ok("Archive pet successfully",
                petService.archive(petId, CurrentUser.require().id()));
    }
}
