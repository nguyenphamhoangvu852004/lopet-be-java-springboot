package com.nguyenvu.lopet.notification;

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

import com.nguyenvu.lopet.common.response.ApiResponse;
import com.nguyenvu.lopet.notification.dto.NotificationDtos;
import com.nguyenvu.lopet.realtime.RealtimeGateway;
import com.nguyenvu.lopet.security.Auth;
import com.nguyenvu.lopet.security.CurrentUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final RealtimeGateway realtimeGateway;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Auth
    public ApiResponse<NotificationDtos.CreateNotificationResponse> create(
            @RequestBody NotificationDtos.CreateNotificationRequest request) {
        // actor luôn là người gọi, lấy từ token
        NotificationDtos.CreateNotificationResponse response = notificationService.create(
                CurrentUser.require().id(), request.receptorId(), request.content(), request.objectType());

        realtimeGateway.emit(RealtimeGateway.userRoom(response.receptorId()),
                RealtimeGateway.EVENT_NOTIFICATION,
                new NotificationDtos.NotificationEvent(response.notificationId(), response.actorId(),
                        response.receptorId(), response.content(), response.objectType(),
                        response.objectId(), response.status(), response.createdAt()));

        return ApiResponse.created("Create notification successfully", response);
    }

    /**
     * Không kiểm người gọi có phải {@code receptor} hay không — bản TS cũng vậy. Đây là hành vi hiện
     * tại của backend, được ghi lại nguyên trạng ở MIGRATION_FINAL_REPORT thay vì tự ý siết thêm.
     */
    @GetMapping("/{id}")
    @Auth
    public ApiResponse<NotificationDtos.NotificationDetail> getDetail(@PathVariable Integer id) {
        return ApiResponse.ok("Get notification detail successfully", notificationService.getDetail(id));
    }

    /** {@code :id} bị bỏ qua — danh sách luôn là của chính người gọi */
    @GetMapping("/me/{id}")
    @Auth
    public ApiResponse<List<NotificationDtos.NotificationListItem>> getList(@PathVariable Integer id) {
        return ApiResponse.ok("Get list notification successfully",
                notificationService.getList(CurrentUser.require().id()));
    }

    @PutMapping("/{id}")
    @Auth
    public ApiResponse<NotificationDtos.UpdateNotificationResponse> update(
            @PathVariable Integer id, @RequestBody NotificationDtos.UpdateNotificationRequest request) {
        NotificationDtos.UpdateNotificationResponse response =
                notificationService.updateStatus(id, request.status());

        // Phòng object_<id> chứ không phải phòng người dùng — giữ đúng bản gốc
        realtimeGateway.emit(RealtimeGateway.objectRoom(id), RealtimeGateway.EVENT_CHANGE_STATUS, response);

        return ApiResponse.ok("Update notification status successfully", response);
    }
}
