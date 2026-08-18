package com.nguyenvu.lopet.friendship;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.common.response.ApiResponse;
import com.nguyenvu.lopet.common.response.HttpStatusMessage;
import com.nguyenvu.lopet.friendship.dto.FriendshipDtos;
import com.nguyenvu.lopet.friendship.entity.FriendshipStatus;
import com.nguyenvu.lopet.security.Auth;
import com.nguyenvu.lopet.security.CurrentUser;

import com.nguyenvu.lopet.security.petcontext.RequireAnyPet;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/friendships")
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService friendshipService;

    /**
     * Danh sách bạn bè của một tài khoản: chỉ chính chủ hoặc bạn bè (ACCEPTED) của họ mới xem được.
     *
     * <p>Đây là dạng quyền mà cả RBAC lẫn ownership đều không diễn tả được: không phải "bạn là ai",
     * cũng không phải "bạn có sở hữu cái này không", mà là "bạn có quan hệ gì với chủ tài nguyên".
     *
     * <p>ADMIN KHÔNG được bỏ qua: danh sách bạn bè là dữ liệu riêng tư, không phải thứ kiểm duyệt
     * viên tự ý xem. Vi phạm thì đi qua luồng báo cáo.
     */
    @GetMapping("/{id}")
    @Auth
    public ApiResponse<FriendshipDtos.FriendshipListResponse> getFriends(@PathVariable Integer id) {
        Integer callerId = CurrentUser.require().id();
        if (!friendshipService.areFriends(callerId, id)) {
            throw new ForbiddenException("Chỉ bạn bè mới xem được danh sách bạn bè của tài khoản này");
        }
        return ApiResponse.ok(HttpStatusMessage.OK, friendshipService.getFriends(id));
    }

    /**
     * {@code :id} trên đường dẫn CỐ Ý bị bỏ qua ở hai endpoint dưới đây — controller TS dùng
     * {@code req.user.id}, nên chúng luôn trả dữ liệu của chính người gọi.
     */
    @GetMapping("/send/{id}")
    @Auth
    public ApiResponse<FriendshipDtos.FriendshipListResponse> getSent(@PathVariable Integer id) {
        return ApiResponse.ok("Get list send friend ship successfully",
                friendshipService.getSentRequests(CurrentUser.require().id()));
    }

    @GetMapping("/receive/{id}")
    @Auth
    public ApiResponse<FriendshipDtos.FriendshipListResponse> getReceived(@PathVariable Integer id) {
        return ApiResponse.ok("Get list send friend ship successfully",
                friendshipService.getReceivedRequests(CurrentUser.require().id()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Auth
    @RequireAnyPet
    public ApiResponse<FriendshipDtos.CreateFriendshipResponse> create(
            @RequestBody FriendshipDtos.CreateFriendshipRequest request) {
        return ApiResponse.created("Create friend ship successfully",
                friendshipService.create(CurrentUser.require().id(), request.receiverId()));
    }

    @PostMapping("/accept")
    @Auth
    @RequireAnyPet
    public ApiResponse<FriendshipDtos.ChangeStatusResponse> accept(
            @RequestBody FriendshipDtos.ChangeStatusRequest request) {
        return ApiResponse.ok("Accept friend ship successfully",
                friendshipService.changeStatus(request.senderId(), CurrentUser.require().id(),
                        FriendshipStatus.ACCEPTED));
    }

    @PostMapping("/reject")
    @Auth
    @RequireAnyPet
    public ApiResponse<FriendshipDtos.ChangeStatusResponse> reject(
            @RequestBody FriendshipDtos.ChangeStatusRequest request) {
        return ApiResponse.ok("Reject friend ship successfully",
                friendshipService.changeStatus(request.senderId(), CurrentUser.require().id(),
                        FriendshipStatus.REJECTED));
    }

    @DeleteMapping
    @Auth
    @RequireAnyPet
    public ApiResponse<FriendshipDtos.DeleteFriendshipResponse> delete(
            @RequestBody FriendshipDtos.DeleteFriendshipRequest request) {
        return ApiResponse.ok("Delete friend ship successfully",
                friendshipService.delete(CurrentUser.require().id(), request.friendId()));
    }
}
