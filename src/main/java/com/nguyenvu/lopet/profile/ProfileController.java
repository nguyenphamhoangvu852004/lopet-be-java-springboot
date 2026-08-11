package com.nguyenvu.lopet.profile;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.nguyenvu.lopet.common.media.CloudinaryService;
import com.nguyenvu.lopet.common.response.ApiResponse;
import com.nguyenvu.lopet.profile.dto.ProfileDtos;
import com.nguyenvu.lopet.security.Auth;
import com.nguyenvu.lopet.security.CurrentUser;
import com.nguyenvu.lopet.security.RequirePermission;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final ProfileAccessGuard profileAccessGuard;
    private final CloudinaryService cloudinaryService;

    /* ---------- Đọc: công khai ---------- */

    @GetMapping
    public ApiResponse<List<ProfileDtos.ProfileSummary>> getList(
            @RequestParam(required = false) Integer id,
            @RequestParam(required = false) String fullName) {
        return ApiResponse.ok("Get profile successfully", profileService.findAll(id, fullName));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProfileDtos.ProfileEntity> getById(@PathVariable Integer id) {
        return ApiResponse.ok("Get profile successfully", profileService.findById(id));
    }

    @GetMapping("/accounts/{id}")
    public ApiResponse<ProfileDtos.ProfileSummary> getByAccountId(@PathVariable Integer id) {
        return ApiResponse.ok("Get profile successfully", profileService.findByAccountId(id));
    }

    /* ---------- Ghi: bắt buộc xác thực ---------- */

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Auth
    @RequirePermission("profile:update:own")
    public ApiResponse<ProfileDtos.ProfileSummary> create(
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String bio,
            @RequestParam(required = false) String dateOfBirth,
            @RequestParam(required = false) String hometown,
            @RequestParam(required = false) Integer sex,
            @RequestPart(name = "avatar", required = false) MultipartFile avatar,
            @RequestPart(name = "cover", required = false) MultipartFile cover) {
        return ApiResponse.ok("Create profile successfully",
                profileService.create(fullName, phoneNumber, bio, parseDate(dateOfBirth), hometown, sex,
                        uploadOrEmpty(avatar), uploadOrEmpty(cover)));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Auth
    @RequirePermission("profile:update:own")
    public ApiResponse<ProfileDtos.ProfileSummary> createJson(
            @RequestBody ProfileDtos.CreateProfileRequest request) {
        return ApiResponse.ok("Create profile successfully",
                profileService.create(request.fullName(), request.phoneNumber(), request.bio(),
                        parseDate(request.dateOfBirth()), request.hometown(), request.sex(), "", ""));
    }

    /** Gắn hồ sơ {@code :id} vào tài khoản CỦA NGƯỜI GỌI — accountId lấy từ token, không từ body */
    @PostMapping("/{id}")
    @Auth
    @RequirePermission("profile:update:own")
    public ApiResponse<ProfileDtos.ProfileShort> setToAccount(@PathVariable Integer id) {
        return ApiResponse.ok("Set profile to account successfully",
                profileService.setToAccount(id, CurrentUser.require().id()));
    }

    /**
     * Ownership kiểm TRƯỚC khi upload để request bị từ chối không tốn lượt upload lên Cloudinary.
     *
     * <p>Hai trường ảnh luôn được truyền xuống dưới dạng chuỗi rỗng khi không có file — đó là hành
     * vi của controller cũ và nó XOÁ ảnh hiện có. Xem ghi chú ở {@code ProfileService.update}.
     */
    @PatchMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Auth
    @RequirePermission("profile:update:own")
    public ApiResponse<ProfileDtos.ProfileEntity> update(
            @PathVariable Integer id,
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String bio,
            @RequestParam(required = false) String dateOfBirth,
            @RequestParam(required = false) String hometown,
            @RequestParam(required = false) Integer sex,
            @RequestPart(name = "avatar", required = false) MultipartFile avatar,
            @RequestPart(name = "cover", required = false) MultipartFile cover) {
        profileAccessGuard.requireOwner(id);
        return ApiResponse.ok("Update profile successfully",
                profileService.update(id, fullName, phoneNumber, bio, uploadOrEmpty(avatar),
                        uploadOrEmpty(cover), parseDate(dateOfBirth), hometown, sex));
    }

    @PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Auth
    @RequirePermission("profile:update:own")
    public ApiResponse<ProfileDtos.ProfileEntity> updateJson(@PathVariable Integer id,
                                                              @RequestBody ProfileDtos.UpdateProfileRequest request) {
        profileAccessGuard.requireOwner(id);
        return ApiResponse.ok("Update profile successfully",
                profileService.update(id, request.fullName(), request.phoneNumber(), request.bio(),
                        "", "", parseDate(request.dateOfBirth()), request.hometown(), request.sex()));
    }

    private String uploadOrEmpty(MultipartFile file) {
        return file == null || file.isEmpty() ? "" : cloudinaryService.uploadImage(file);
    }

    private LocalDate parseDate(String value) {
        return value == null || value.isEmpty() ? null : LocalDate.parse(value.substring(0, 10));
    }
}
