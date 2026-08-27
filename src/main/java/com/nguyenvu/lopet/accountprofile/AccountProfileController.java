package com.nguyenvu.lopet.accountprofile;

import java.time.LocalDate;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.nguyenvu.lopet.accountprofile.dto.AccountProfileDtos;
import com.nguyenvu.lopet.common.media.CloudinaryService;
import com.nguyenvu.lopet.common.response.ApiResponse;
import com.nguyenvu.lopet.security.Auth;
import com.nguyenvu.lopet.security.CurrentUser;
import com.nguyenvu.lopet.security.RequirePermission;

import lombok.RequiredArgsConstructor;

/**
 * Hồ sơ tài khoản — thực thể hiển thị của một người dùng trên mạng xã hội.
 *
 * <p>Ba endpoint đọc cũ ({@code GET /v1/profiles}, {@code GET /v1/profiles/{id}},
 * {@code GET /v1/profiles/accounts/{id}}) đã bị xoá vì chúng không lọc quyền: số điện thoại, ngày
 * sinh, quê quán của bất kỳ ai cũng đọc được — kể cả bởi khách chưa đăng nhập — chỉ cần biết một id.
 * Đường đọc hồ sơ người khác nay đi qua {@code GET /v1/account-profiles/accounts/{id}} và bị chặn
 * bởi {@link com.nguyenvu.lopet.accountprofile.repository.AccountProfileVisibilityFilter}.
 *
 * <p>Người dùng KHÔNG tự tạo hồ sơ: mỗi tài khoản được cấp sẵn một hồ sơ ngay lúc đăng ký
 * ({@link AccountProfileFactory}). Không endpoint nào nhận {@code profileId} — hồ sơ luôn tra bằng
 * danh tính trong token, nên module này không cần tầng ownership.
 */
@RestController
@RequestMapping("/v1/account-profiles")
@RequiredArgsConstructor
public class AccountProfileController {

    private final AccountProfileService profileService;
    private final CloudinaryService cloudinaryService;

    /* ---------- Đọc: chỉ chính chủ ---------- */

    @GetMapping("/me")
    @Auth
    public ApiResponse<AccountProfileDtos.ProfileSummary> getMine() {
        return ApiResponse.ok("Get my profile successfully",
                profileService.findByAccountId(CurrentUser.require().id()));
    }

    /**
     * Hồ sơ của người khác, lọc theo {@code visibility} của chính hồ sơ đó.
     *
     * <p>{@code @Auth(required = false)}: hồ sơ PUBLIC đọc được bởi khách vãng lai, nên bắt đăng nhập
     * sẽ chặn nhầm. Người đã đăng nhập thì mở thêm nhánh chính chủ và nhánh bạn bè.
     */
    @GetMapping("/accounts/{id}")
    @Auth(required = false)
    public ApiResponse<AccountProfileDtos.PublicProfile> getByAccountId(@PathVariable Integer id) {
        return ApiResponse.ok("Get profile successfully",
                profileService.findVisibleByAccountId(id, CurrentUser.viewerId()));
    }

    /* ---------- Ghi: chỉ hồ sơ của chính người gọi ---------- */

    /**
     * {@code avatar}/{@code cover} chỉ được ghi khi request thật sự đính file — {@link #uploadOrNull}
     * trả null khi không có, và {@code updateMine} bỏ qua null. Bản cũ luôn truyền chuỗi rỗng nên
     * mỗi lần sửa bio không kèm ảnh là xoá luôn avatar.
     */
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Auth
    @RequirePermission("accountProfile:update:own")
    public ApiResponse<AccountProfileDtos.ProfileEntity> update(
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String bio,
            @RequestParam(required = false) String dateOfBirth,
            @RequestParam(required = false) String hometown,
            @RequestParam(required = false) Integer sex,
            @RequestParam(required = false) String visibility,
            @RequestPart(name = "avatar", required = false) MultipartFile avatar,
            @RequestPart(name = "cover", required = false) MultipartFile cover) {
        return ApiResponse.ok("Update profile successfully",
                profileService.updateMine(CurrentUser.require().id(), fullName, phoneNumber, bio,
                        uploadOrNull(avatar), uploadOrNull(cover), parseDate(dateOfBirth), hometown, sex,
                        visibility));
    }

    /** null (không phải chuỗi rỗng) khi không có file — đó là tín hiệu "giữ nguyên ảnh cũ" */
    private String uploadOrNull(MultipartFile file) {
        return file == null || file.isEmpty() ? null : cloudinaryService.uploadImage(file);
    }

    private LocalDate parseDate(String value) {
        return value == null || value.isEmpty() ? null : LocalDate.parse(value.substring(0, 10));
    }
}
