package com.nguyenvu.lopet.advertisement;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.nguyenvu.lopet.advertisement.dto.AdvertisementDtos;
import com.nguyenvu.lopet.advertiser.AdvertiserService;
import com.nguyenvu.lopet.common.media.CloudinaryService;
import com.nguyenvu.lopet.common.response.ApiResponse;
import com.nguyenvu.lopet.security.Auth;
import com.nguyenvu.lopet.security.CurrentUser;
import com.nguyenvu.lopet.security.RequirePermission;

import lombok.RequiredArgsConstructor;

/**
 * Ba tầng kiểm tra cho một thao tác ghi, đúng thứ tự của bản TS:
 * <ol>
 *   <li>{@code @RequirePermission} — hành động này có được phép không</li>
 *   <li>{@code requireApproved} — có tư cách chạy quảng cáo chưa (APPROVED)</li>
 *   <li>ownership guard — đúng quảng cáo của mình không (ADMIN bỏ qua)</li>
 * </ol>
 * Cả ba phải chạy TRƯỚC khi upload ảnh, vì đó mới là bước tốn kém thật sự.
 */
@RestController
@RequestMapping("/v1/advertisements")
@RequiredArgsConstructor
public class AdvertisementController {

    private final AdvertisementService advertisementService;
    private final AdvertisementAccessGuard advertisementAccessGuard;
    private final AdvertiserService advertiserService;
    private final CloudinaryService cloudinaryService;

    @GetMapping
    public ApiResponse<List<AdvertisementDtos.AdvertisementDetail>> getList(
            @RequestParam(required = false) Integer accountId) {
        return ApiResponse.ok("Get list advertisement successfully", advertisementService.getList(accountId));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdvertisementDtos.AdvertisementDetail> getDetail(@PathVariable Integer id) {
        return ApiResponse.ok("Get detail advertisement successfully", advertisementService.getDetail(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Auth
    @RequirePermission("ads:create")
    public ApiResponse<AdvertisementDtos.IdResponse> create(
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam(required = false) String linkRef,
            @RequestPart(name = "image") MultipartFile image) {
        Integer accountId = CurrentUser.require().id();
        advertiserService.requireApproved(accountId);

        String imageUrl = cloudinaryService.upload(image, CloudinaryService.IMAGE);
        return ApiResponse.created("Create advertisement successfully",
                advertisementService.create(accountId, title, description, linkRef, imageUrl));
    }

    @PutMapping(path = "/{adsId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Auth
    @RequirePermission("ads:update:own")
    public ApiResponse<AdvertisementDtos.IdResponse> update(
            @PathVariable Integer adsId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String linkRef,
            @RequestPart(name = "image", required = false) MultipartFile image) {
        advertisementAccessGuard.requireOwner(adsId);

        String imageUrl = image == null || image.isEmpty()
                ? null
                : cloudinaryService.upload(image, CloudinaryService.IMAGE);
        return ApiResponse.ok("Update advertisement successfully",
                advertisementService.update(adsId, title, description, linkRef, imageUrl));
    }

    @PutMapping(path = "/{adsId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Auth
    @RequirePermission("ads:update:own")
    public ApiResponse<AdvertisementDtos.IdResponse> updateJson(
            @PathVariable Integer adsId, @RequestBody AdvertisementDtos.CreateAdvertisementRequest request) {
        advertisementAccessGuard.requireOwner(adsId);
        return ApiResponse.ok("Update advertisement successfully",
                advertisementService.update(adsId, request.title(), request.description(),
                        request.linkRef(), null));
    }

    @DeleteMapping("/{id}")
    @Auth
    @RequirePermission("ads:delete:own")
    public ApiResponse<AdvertisementDtos.DeleteResponse> delete(@PathVariable Integer id) {
        advertisementAccessGuard.requireOwner(id);
        return ApiResponse.ok("Delete advertisement successfully", advertisementService.delete(id));
    }
}
