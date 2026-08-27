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
 * Các route liệt kê ở đây KHÔNG có xác thực, đúng như bản TS. Nhưng {@code GET /{id}} thì có
 * {@code @Auth(required = false)}: metadata nhóm vẫn công khai, còn DANH SÁCH THÀNH VIÊN của một nhóm
 * PRIVATE thì không — nó cần biết người xem là ai để quyết định che hay không, xem
 * {@code GroupService#getById}. Bài viết trong nhóm vẫn do tầng visibility của post lo.
 *
 * <p><b>Ba mức phân quyền cho các route ghi.</b> Tự phục vụ (tham gia, rời nhóm, mời, trả lời lời
 * mời) chỉ cần {@code @Auth} — luật nghiệp vụ thật nằm ở service, và không mã quyền toàn
 * cục nào diễn tả được "là thành viên của đúng nhóm này". Route của quản trị nhóm dùng lại mã
 * {@code group:update:own} có sẵn, nên {@code PermissionCatalog} không phải đổi. Xoá nhóm thì thêm
 * {@code group:delete}.
 *
 * <p><b>Các route ghi chỉ nhận {@code multipart/form-data}.</b> Trước đây mỗi route có thêm một
 * biến thể JSON, tái hiện việc bên Express {@code express.json()} + {@code multer} cùng gắn lên
 * một handler nên body kiểu nào cũng chạy. Biến thể đó đã bỏ: không client nào gọi, mà nó nhân đôi
 * chỗ khai {@code @RequirePermission} — sửa quyền một bên quên bên kia là mở ra một đường vòng
 * không test nào bắt được.
 *
 * <p>Vì vậy {@code consumes} phải giữ TƯỜNG MINH dù giờ mỗi route chỉ còn một handler: bỏ nó đi
 * thì một request JSON sẽ khớp vào handler multipart và chết ở bước bind {@code @RequestPart} với
 * một lỗi khó hiểu, thay vì {@code 415 Unsupported Media Type} đúng nghĩa.
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

    /**
     * {@code @Auth(required = false)}: khách vãng lai vẫn xem được metadata, người đã đăng nhập thì
     * thấy thêm danh sách thành viên nếu họ ở trong nhóm.
     */
    @GetMapping("/{id}")
    @Auth(required = false)
    public ApiResponse<GroupDtos.GroupDetail> getById(@PathVariable Integer id) {
        return ApiResponse.ok("Get group successfully",
                groupService.getById(id, CurrentUser.viewerId()));
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

    private ApiResponse<GroupDtos.CreateGroupResponse> created(String name, String type, String bio,
                                                                String coverUrl) {
        return ApiResponse.created("Create group successfully",
                groupService.create(name, type, bio, coverUrl));
    }

    /* ---------- Tham gia / rời nhóm ---------- */

    /**
     * Nhóm PUBLIC: vào ngay. Nhóm PRIVATE: tạo yêu cầu chờ quản trị nhóm duyệt. Đọc
     * {@code status} trong phản hồi để biết đã vào hay đang chờ.
     */
    @PostMapping("/{id}/join")
    @ResponseStatus(HttpStatus.CREATED)
    @Auth
    public ApiResponse<GroupDtos.JoinGroupResponse> join(@PathVariable Integer id) {
        return ApiResponse.created("Join group successfully", groupService.join(id));
    }

    /** Huỷ yêu cầu tham gia mà chính mình đã gửi và chưa được duyệt */
    @DeleteMapping("/{id}/join")
    @Auth
    public ApiResponse<GroupDtos.LeaveGroupResponse> cancelJoinRequest(@PathVariable Integer id) {
        return ApiResponse.ok("Cancel join request successfully", groupService.cancelJoinRequest(id));
    }

    @DeleteMapping("/{id}/leave")
    @Auth
    public ApiResponse<GroupDtos.LeaveGroupResponse> leave(@PathVariable Integer id) {
        return ApiResponse.ok("Leave group successfully", groupService.leave(id));
    }

    /* ---------- Yêu cầu vào nhóm: quản trị nhóm duyệt ---------- */

    @GetMapping("/{id}/requests")
    @Auth
    @RequirePermission("group:update:own")
    public ApiResponse<List<GroupDtos.PendingMemberView>> getListJoinRequests(@PathVariable Integer id) {
        return ApiResponse.ok("Get join requests successfully", groupService.listJoinRequests(id));
    }

    @PostMapping("/requests/approve")
    @Auth
    @RequirePermission("group:update:own")
    public ApiResponse<GroupDtos.JoinGroupResponse> approveJoinRequest(
            @RequestBody GroupDtos.ReviewMemberRequest request) {
        return ApiResponse.ok("Approve join request successfully",
                groupService.reviewJoinRequest(request.groupId(), request.accountId(), true));
    }

    @PostMapping("/requests/reject")
    @Auth
    @RequirePermission("group:update:own")
    public ApiResponse<GroupDtos.JoinGroupResponse> rejectJoinRequest(
            @RequestBody GroupDtos.ReviewMemberRequest request) {
        return ApiResponse.ok("Reject join request successfully",
                groupService.reviewJoinRequest(request.groupId(), request.accountId(), false));
    }

    /* ---------- Lời mời: chính người được mời duyệt ---------- */

    /**
     * Mời một thú cưng khác. <b>Không còn là lệnh thêm thành viên thẳng</b> như trước: nó tạo một lời
     * mời PENDING và người được mời phải tự chấp nhận. Vì vậy quyền cũng nới từ quản trị nhóm xuống
     * mọi thành viên ACTIVE — một hàng PENDING không cấp quyền đọc gì.
     */
    @PostMapping("/invites")
    @ResponseStatus(HttpStatus.CREATED)
    @Auth
    public ApiResponse<GroupDtos.InviteResponse> invite(@RequestBody GroupDtos.AddMemberRequest request) {
        return ApiResponse.created("Invite member successfully",
                groupService.invite(request.groupId(), request.invitee()));
    }

    /** Hộp thư lời mời của tài khoản đang đăng nhập — không nhận id trong URL */
    @GetMapping("/invites/mine")
    @Auth
    public ApiResponse<List<GroupDtos.PendingInviteView>> getMyInvites() {
        return ApiResponse.ok("Get invites successfully", groupService.listMyInvites());
    }

    @PostMapping("/invites/accept")
    @Auth
    public ApiResponse<GroupDtos.JoinGroupResponse> acceptInvite(
            @RequestBody GroupDtos.GroupIdRequest request) {
        return ApiResponse.ok("Accept invite successfully",
                groupService.respondToInvite(request.groupId(), true));
    }

    @PostMapping("/invites/reject")
    @Auth
    public ApiResponse<GroupDtos.JoinGroupResponse> rejectInvite(
            @RequestBody GroupDtos.GroupIdRequest request) {
        return ApiResponse.ok("Reject invite successfully",
                groupService.respondToInvite(request.groupId(), false));
    }

    @DeleteMapping
    @Auth
    @RequirePermission({"group:delete:own", "group:delete"})
    public ApiResponse<GroupDtos.DeleteGroupResponse> delete(@RequestBody GroupDtos.DeleteGroupRequest request) {
        return ApiResponse.ok("Delete group successfully",
                groupService.delete(request.groupId()));
    }

    @DeleteMapping("/members")
    @Auth
    @RequirePermission("group:update:own")
    public ApiResponse<GroupDtos.RemoveMemberResponse> removeMember(
            @RequestBody GroupDtos.RemoveMemberRequest request) {
        return ApiResponse.ok("Remove member successfully",
                groupService.removeMember(request.groupId(), request.member()));
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

    private ApiResponse<GroupDtos.ModifyGroupResponse> modified(Integer id, String name, String type,
                                                                 String bio, String coverUrl) {
        String normalizedType = "PUBLIC".equals(type) ? "PUBLIC" : "PRIVATE";
        return ApiResponse.ok("Modify group successfully",
                groupService.modify(id, name, normalizedType, bio, coverUrl));
    }
}
