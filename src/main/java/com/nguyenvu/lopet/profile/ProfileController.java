package com.nguyenvu.lopet.profile;

import java.time.LocalDate;
import java.util.List;

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
import com.nguyenvu.lopet.profile.dto.ProfileDtos;
import com.nguyenvu.lopet.security.Auth;
import com.nguyenvu.lopet.security.CurrentUser;
import com.nguyenvu.lopet.security.RequirePermission;

import lombok.RequiredArgsConstructor;

/**
 * Người dùng KHÔNG tự tạo hồ sơ: mỗi tài khoản được cấp sẵn một hồ sơ ngay lúc đăng ký
 * ({@link ProfileFactory}), nên ở đây chỉ còn một endpoint ghi duy nhất.
 *
 * <p>Không endpoint ghi nào nhận {@code profileId} nữa — hồ sơ luôn được tra bằng danh tính trong
 * token. Nhờ vậy module này không cần tầng ownership: mô hình cũ phải có {@code ProfileAccessGuard}
 * vì {@code POST /v1/profiles/{id}} cho phép gắn hồ sơ của người khác vào tài khoản mình.
 */
@RestController
@RequestMapping("/v1/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final CloudinaryService cloudinaryService;

    /* ---------- Đọc: công khai ---------- */

    @GetMapping
    public ApiResponse<List<ProfileDtos.ProfileSummary>> getList(
            @RequestParam(required = false) Integer id,
            @RequestParam(required = false) String fullName) {
        return ApiResponse.ok("Get profile successfully", profileService.findAll(id, fullName));
    }

    /**
     * Đặt TRƯỚC {@code /{id}} cho dễ đọc; Spring vốn ưu tiên path cố định hơn path variable nên
     * thứ tự khai báo không phải là thứ quyết định.
     */
    @GetMapping("/me")
    @Auth
    public ApiResponse<ProfileDtos.ProfileSummary> getMine() {
        return ApiResponse.ok("Get my profile successfully",
                profileService.findByAccountId(CurrentUser.require().id()));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProfileDtos.ProfileEntity> getById(@PathVariable Integer id) {
        return ApiResponse.ok("Get profile successfully", profileService.findById(id));
    }

    @GetMapping("/accounts/{id}")
    public ApiResponse<ProfileDtos.ProfileSummary> getByAccountId(@PathVariable Integer id) {
        return ApiResponse.ok("Get profile successfully", profileService.findByAccountId(id));
    }

    /* ---------- Ghi: chỉ hồ sơ của chính người gọi ---------- */

    /**
     * {@code avatar}/{@code cover} chỉ được ghi khi request thật sự đính file — {@link #uploadOrNull}
     * trả null khi không có, và {@code updateMine} bỏ qua null. Bản cũ luôn truyền chuỗi rỗng nên
     * mỗi lần sửa bio không kèm ảnh là xoá luôn avatar.
     */
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Auth
    @RequirePermission("profile:update:own")
    public ApiResponse<ProfileDtos.ProfileEntity> update(
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String bio,
            @RequestParam(required = false) String dateOfBirth,
            @RequestParam(required = false) String hometown,
            @RequestParam(required = false) Integer sex,
            @RequestPart(name = "avatar", required = false) MultipartFile avatar,
            @RequestPart(name = "cover", required = false) MultipartFile cover) {
        return ApiResponse.ok("Update profile successfully",
                profileService.updateMine(CurrentUser.require().id(), fullName, phoneNumber, bio,
                        uploadOrNull(avatar), uploadOrNull(cover), parseDate(dateOfBirth), hometown, sex));
    }

    /** Biến thể JSON: không nhận file nên hai trường ảnh luôn là null, tức là giữ nguyên ảnh cũ */
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Auth
    @RequirePermission("profile:update:own")
    public ApiResponse<ProfileDtos.ProfileEntity> updateJson(
            @RequestBody ProfileDtos.UpdateProfileRequest request) {
        return ApiResponse.ok("Update profile successfully",
                profileService.updateMine(CurrentUser.require().id(), request.fullName(),
                        request.phoneNumber(), request.bio(), null, null,
                        parseDate(request.dateOfBirth()), request.hometown(), request.sex()));
    }

    /** null (không phải chuỗi rỗng) khi không có file — đó là tín hiệu "giữ nguyên ảnh cũ" */
    private String uploadOrNull(MultipartFile file) {
        return file == null || file.isEmpty() ? null : cloudinaryService.uploadImage(file);
    }

    private LocalDate parseDate(String value) {
        return value == null || value.isEmpty() ? null : LocalDate.parse(value.substring(0, 10));
    }
}
