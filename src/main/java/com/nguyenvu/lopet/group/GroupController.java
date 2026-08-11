package com.nguyenvu.lopet.group;

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

import com.nguyenvu.lopet.common.media.CloudinaryService;
import com.nguyenvu.lopet.common.response.ApiResponse;
import com.nguyenvu.lopet.group.dto.GroupDtos;
import com.nguyenvu.lopet.security.Auth;
import com.nguyenvu.lopet.security.CurrentUser;
import com.nguyenvu.lopet.security.RequirePermission;

import lombok.RequiredArgsConstructor;

/**
 * Các route đọc ở đây KHÔNG có xác thực, đúng như bản TS — thông tin nhóm (kể cả nhóm PRIVATE) là
 * công khai; thứ được bảo vệ là BÀI VIẾT trong nhóm, do tầng visibility của post lo.
 *
 * <p>Mỗi endpoint ghi có hai biến thể: một nhận {@code multipart/form-data} (có ảnh) và một nhận
 * JSON (không ảnh). Bên Express cả hai kiểu body đều chạy qua cùng một handler nhờ
 * {@code express.json()} + {@code multer}, nên bỏ biến thể JSON sẽ làm hỏng client đang gửi JSON.
 */
@RestController
@RequestMapping("/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final CloudinaryService cloudinaryService;

    @GetMapping("/suggest")
    public ApiResponse<List<GroupDtos.GroupSummary>> getListSuggest() {
        // Khoảng trắng đầu chuỗi message là nguyên văn của bản TS, giữ lại cho khớp response cũ
        return ApiResponse.ok(" Get group successfully", groupService.getListSuggest());
    }

    @GetMapping("/{id}")
    public ApiResponse<GroupDtos.GroupDetail> getById(@PathVariable Integer id) {
        return ApiResponse.ok("Get group successfully", groupService.getById(id));
    }

    @GetMapping("/owned/{id}")
    public ApiResponse<List<GroupDtos.GroupSummary>> getListOwned(@PathVariable Integer id) {
        return ApiResponse.ok(" Get group successfully", groupService.getListOwned(id));
    }

    @GetMapping("/joined/{id}")
    public ApiResponse<List<GroupDtos.GroupSummary>> getListJoined(@PathVariable Integer id) {
        return ApiResponse.ok(" Get group successfully", groupService.getListJoined(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Auth
    @RequirePermission("group:create")
    public ApiResponse<GroupDtos.CreateGroupResponse> create(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String bio,
            @RequestPart(name = "image", required = false) MultipartFile image) {
        String coverUrl = image == null || image.isEmpty() ? "" : cloudinaryService.uploadImage(image);
        return created(name, type, bio, coverUrl);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Auth
    @RequirePermission("group:create")
    public ApiResponse<GroupDtos.CreateGroupResponse> createJson(
            @RequestBody GroupDtos.CreateGroupRequest request) {
        return created(request.name(), request.type(), request.bio(), "");
    }

    private ApiResponse<GroupDtos.CreateGroupResponse> created(String name, String type, String bio,
                                                                String coverUrl) {
        return ApiResponse.created("Create group successfully",
                groupService.create(name, type, bio, coverUrl, CurrentUser.require().id()));
    }

    @PostMapping("/invites")
    @ResponseStatus(HttpStatus.CREATED)
    @Auth
    @RequirePermission("group:update:own")
    public ApiResponse<GroupDtos.AddMemberResponse> addMember(@RequestBody GroupDtos.AddMemberRequest request) {
        return ApiResponse.created("Add member successfully",
                groupService.addMember(request.groupId(), request.invitee(), CurrentUser.require().id()));
    }

    @DeleteMapping
    @Auth
    @RequirePermission({"group:delete:own", "group:delete"})
    public ApiResponse<GroupDtos.DeleteGroupResponse> delete(@RequestBody GroupDtos.DeleteGroupRequest request) {
        return ApiResponse.ok("Delete group successfully",
                groupService.delete(request.groupId(), CurrentUser.require().id()));
    }

    @DeleteMapping("/members")
    @Auth
    @RequirePermission("group:update:own")
    public ApiResponse<GroupDtos.RemoveMemberResponse> removeMember(
            @RequestBody GroupDtos.RemoveMemberRequest request) {
        return ApiResponse.ok("Remove member successfully",
                groupService.removeMember(request.groupId(), request.member(), CurrentUser.require().id()));
    }

    /**
     * {@code type} được áp dụng KỂ CẢ khi client không gửi: controller TS tính
     * {@code type == 'PUBLIC' ? PUBLIC : PRIVATE} nên thiếu trường này đồng nghĩa nhóm chuyển thành
     * PRIVATE. Đây là hành vi thật của backend cũ, không phải lỗi dịch.
     */
    @PutMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Auth
    @RequirePermission("group:update:own")
    public ApiResponse<GroupDtos.ModifyGroupResponse> modify(
            @PathVariable Integer id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String bio,
            @RequestPart(name = "image", required = false) MultipartFile image) {
        String coverUrl = image == null || image.isEmpty() ? null : cloudinaryService.uploadImage(image);
        return modified(id, name, type, bio, coverUrl);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Auth
    @RequirePermission("group:update:own")
    public ApiResponse<GroupDtos.ModifyGroupResponse> modifyJson(@PathVariable Integer id,
                                                                  @RequestBody GroupDtos.ModifyGroupRequest request) {
        return modified(id, request.name(), request.type(), request.bio(), null);
    }

    private ApiResponse<GroupDtos.ModifyGroupResponse> modified(Integer id, String name, String type,
                                                                 String bio, String coverUrl) {
        String normalizedType = "PUBLIC".equals(type) ? "PUBLIC" : "PRIVATE";
        return ApiResponse.ok("Modify group successfully",
                groupService.modify(id, name, normalizedType, bio, coverUrl, CurrentUser.require().id()));
    }
}
