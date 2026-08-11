package com.nguyenvu.lopet.notification.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nguyenvu.lopet.notification.entity.NotificationObjectType;
import com.nguyenvu.lopet.notification.entity.NotificationStatus;

/**
 * Lưu ý về đặt tên — đây là bẫy tích hợp đã có thật ở frontend:
 * <ul>
 *   <li>khoá định danh là {@code notificationId}, KHÔNG phải {@code id};</li>
 *   <li>REST trả {@code type}, còn payload socket lúc tạo lại dùng {@code objectType}.</li>
 * </ul>
 * Cả hai đều được giữ nguyên.
 */
public final class NotificationDtos {

    public record CreateNotificationRequest(Integer receptorId, String content, String objectType) {
    }

    public record CreateNotificationResponse(
            Integer notificationId,
            Integer actorId,
            Integer receptorId,
            String content,
            NotificationObjectType objectType,
            NotificationStatus status,
            LocalDateTime createdAt) {
    }

    /** Payload socket của sự kiện {@code notification} — không có notificationId */
    public record NotificationEvent(
            Integer actorId,
            Integer receptorId,
            String content,
            NotificationObjectType objectType,
            NotificationStatus status,
            LocalDateTime createdAt) {
    }

    public record NotificationListItem(
            Integer notificationId,
            Integer actorId,
            Integer receptorId,
            String content,
            LocalDateTime createdAt,
            NotificationStatus status,
            NotificationObjectType type) {
    }

    public record UpdateNotificationRequest(String status) {
    }

    public record UpdateNotificationResponse(
            Integer notificationId,
            Integer actorId,
            Integer receptorId,
            String content,
            LocalDateTime createdAt,
            NotificationStatus status,
            NotificationObjectType type) {
    }

    /** {@code updatedAt}/{@code deletedAt} vắng mặt khi null — bản TS gán {@code ?? undefined} */
    public record NotificationDetail(
            Integer notificationId,
            NotificationAccount actor,
            NotificationAccount receptor,
            String content,
            NotificationStatus status,
            NotificationObjectType type,
            LocalDateTime createdAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) LocalDateTime updatedAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) LocalDateTime deletedAt) {
    }

    /** Không có {@code roles}: bản TS chỉ gán id/username/email rồi đính thêm profile */
    public record NotificationAccount(Integer id, String username, String email, NotificationProfile profile) {
    }

    /**
     * Tài khoản chưa có hồ sơ nhận về một object RỖNG ({@code {}}), vì bản TS dựng
     * {@code new GetProfileOutputDTO()} không gán trường nào — do đó tất cả field ở đây đều bị bỏ
     * khi null.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NotificationProfile(
            Integer id,
            String fullName,
            String phoneNumber,
            String bio,
            String avatarUrl,
            String coverUrl) {
    }

    private NotificationDtos() {
    }
}
