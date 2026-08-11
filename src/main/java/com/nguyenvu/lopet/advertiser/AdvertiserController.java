package com.nguyenvu.lopet.advertiser;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nguyenvu.lopet.advertiser.dto.AdvertiserDtos;
import com.nguyenvu.lopet.common.response.ApiResponse;
import com.nguyenvu.lopet.security.Auth;
import com.nguyenvu.lopet.security.CurrentUser;
import com.nguyenvu.lopet.security.RequirePermission;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/advertisers")
@RequiredArgsConstructor
public class AdvertiserController {

    private final AdvertiserService advertiserService;

    /** Baseline: mọi tài khoản đã đăng nhập đều được nộp hồ sơ (tạo ra bản ghi PENDING) */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Auth
    @RequirePermission("advertiser:register")
    public ApiResponse<AdvertiserDtos.AdvertiserProfileResponse> register(
            @RequestBody AdvertiserDtos.RegisterRequest request) {
        // accountId lấy từ token — không cho client tự khai hộ tài khoản khác
        return ApiResponse.created("Đăng ký hồ sơ nhà quảng cáo thành công, chờ duyệt",
                advertiserService.register(CurrentUser.require().id(), request.companyName()));
    }

    @GetMapping("/me")
    @Auth
    public ApiResponse<AdvertiserDtos.AdvertiserProfileResponse> getMine() {
        return ApiResponse.ok("Get advertiser profile successfully",
                advertiserService.getMine(CurrentUser.require().id()));
    }

    @GetMapping
    @Auth
    @RequirePermission("advertiser:read")
    public ApiResponse<List<AdvertiserDtos.AdvertiserProfileResponse>> getList() {
        return ApiResponse.ok("Get list advertiser profile successfully", advertiserService.getList());
    }

    @PutMapping("/{id}/status")
    @Auth
    @RequirePermission("advertiser:approve")
    public ApiResponse<AdvertiserDtos.AdvertiserProfileResponse> review(
            @PathVariable Integer id, @RequestBody AdvertiserDtos.ReviewRequest request) {
        return ApiResponse.ok("Cập nhật trạng thái hồ sơ nhà quảng cáo thành công",
                advertiserService.review(id, request.status(), CurrentUser.require().id()));
    }
}
