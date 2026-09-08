package com.nguyenvu.lopet.accountprofile;

import java.time.LocalDate;

import com.nguyenvu.lopet.upload.Images;
import com.nguyenvu.lopet.upload.UploadKind;
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
import com.nguyenvu.lopet.upload.CloudinaryService;
import com.nguyenvu.lopet.common.response.ApiResponse;
import com.nguyenvu.lopet.security.Auth;
import com.nguyenvu.lopet.security.CurrentUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/account-profiles")
@RequiredArgsConstructor
public class AccountProfileController {

    private final AccountProfileService profileService;
    private final CloudinaryService cloudinaryService;

    @GetMapping("/me")
    @Auth
    public ApiResponse<AccountProfileDtos.ProfileSummary> getMine() {
        return ApiResponse.ok("Get my profile successfully",
                profileService.findByAccountId(CurrentUser.require().id()));
    }

    @GetMapping("/accounts/{id}")
    public ApiResponse<AccountProfileDtos.PublicProfile> getByAccountId(@PathVariable Integer id) {
        return ApiResponse.ok("Get profile successfully", profileService.findPublicByAccountId(id));
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Auth
    public ApiResponse<AccountProfileDtos.ProfileEntity> update(
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

    private String uploadOrNull(MultipartFile file) {
        return file == null || file.isEmpty() ? null : cloudinaryService.upload(file, UploadKind.IMAGE);
    }

    private LocalDate parseDate(String value) {
        return value == null || value.isEmpty() ? null : LocalDate.parse(value.substring(0, 10));
    }
}
