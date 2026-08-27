package com.nguyenvu.lopet.message;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.nguyenvu.lopet.common.media.CloudinaryService;
import com.nguyenvu.lopet.common.response.ApiResponse;
import com.nguyenvu.lopet.message.dto.MessageDtos;
import com.nguyenvu.lopet.message.entity.MessageStatus;
import com.nguyenvu.lopet.realtime.RealtimeGateway;
import com.nguyenvu.lopet.security.Auth;
import com.nguyenvu.lopet.security.CurrentUser;


import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final MessageAccessGuard messageAccessGuard;
    private final CloudinaryService cloudinaryService;
    private final RealtimeGateway realtimeGateway;
    private final MessageStatusNotifier messageStatusNotifier;

    @GetMapping("/{id}")
    @Auth
    public ApiResponse<MessageDtos.MessageResponse> getDetail(@PathVariable Integer id) {
        messageAccessGuard.requireParticipant(id);
        return ApiResponse.ok("Get detail message successfully", messageService.getDetail(id));
    }

    @PatchMapping("/status/{id}")
    @Auth
    public ApiResponse<MessageDtos.ChangeStatusResponse> updateStatus(
            @PathVariable Integer id, @RequestBody MessageDtos.ChangeStatusRequest request) {
        messageAccessGuard.requireParticipant(id);
        messageStatusNotifier.broadcast(messageService.changeStatus(CurrentUser.require().id(), id,
                MessageStatus.valueOf(request.status())));
        return ApiResponse.ok("Update message successfully",
                new MessageDtos.ChangeStatusResponse(true, "Update message successfully"));
    }

    /**
     * Đường lui REST cho ack "đã nhận", tương đương sự kiện socket {@code message delivered} —
     * dùng khi client không giữ được socket (app vừa mở lại, mạng chập chờn).
     *
     * <p>Không có {@code messageAccessGuard} ở đây một cách CÓ CHỦ Ý: guard làm việc trên từng id
     * và sẽ ném 403 cho cả lô chỉ vì một id lạc. Việc lọc nằm trong câu truy vấn — nó chỉ nhận
     * những tin có {@code receiver} đúng là người gọi, nên id của người khác âm thầm bị bỏ qua.
     */
    @PatchMapping("/delivered")
    @Auth
    public ApiResponse<MessageDtos.MarkStatusResponse> markDelivered(
            @RequestBody MessageDtos.DeliveredAckRequest request) {
        MessageDtos.StatusUpdateResult result =
                messageService.markDelivered(CurrentUser.require().id(), request.messageIds());
        messageStatusNotifier.broadcast(result);
        return ApiResponse.ok("Mark messages delivered successfully",
                new MessageDtos.MarkStatusResponse(result.count(), MessageStatus.DELIVERED));
    }

    /** Đánh dấu đã xem cả hội thoại với {@code partnerId} — một request cho một lần mở cuộc trò chuyện */
    @PatchMapping("/read")
    @Auth
    public ApiResponse<MessageDtos.MarkStatusResponse> markRead(@RequestParam Integer partnerId) {
        MessageDtos.StatusUpdateResult result =
                messageService.markConversationRead(CurrentUser.require().id(), partnerId);
        messageStatusNotifier.broadcast(result);
        return ApiResponse.ok("Mark conversation read successfully",
                new MessageDtos.MarkStatusResponse(result.count(), MessageStatus.READ));
    }

    /**
     * Danh sách id tin đang chờ ack "đã nhận" — client gọi ngay sau khi socket kết nối, rồi ack lại
     * bằng chính hai đường có sẵn ({@code message delivered} hoặc {@code PATCH /delivered}).
     *
     * <p>Không có bước này, tin gửi lúc người nhận đã đăng xuất sẽ kẹt ở "đã gửi" mãi mãi: không
     * socket nào chuyển chúng đi nên chẳng có ack nào được phát, kể cả sau khi họ đăng nhập lại.
     */
    @GetMapping("/pending-delivery")
    @Auth
    public ApiResponse<List<Integer>> pendingDelivery() {
        return ApiResponse.ok("Get pending delivery messages successfully",
                messageService.awaitingDelivery(CurrentUser.require().id()));
    }

    /** Badge tổng số tin chưa đọc của người gọi */
    @GetMapping("/unread-count")
    @Auth
    public ApiResponse<MessageDtos.UnreadCountResponse> unreadCount() {
        return ApiResponse.ok("Get unread count successfully",
                new MessageDtos.UnreadCountResponse(
                        messageService.countUnread(CurrentUser.require().id())));
    }

    /**
     * Người đối thoại lấy từ query {@code ?targetId=}, KHÔNG phải từ {@code :id} trên đường dẫn —
     * controller TS bỏ qua path param và đọc {@code req.query.targetId}. Client hiện tại phụ thuộc
     * đúng hình dạng URL này.
     */
    @GetMapping("/me/{id}")
    @Auth
    public ApiResponse<List<MessageDtos.MessageResponse>> getConversation(@PathVariable Integer id,
                                                                          @RequestParam Integer targetId) {
        return ApiResponse.ok("Get list message successfully",
                messageService.getConversation(CurrentUser.require().id(), targetId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Auth
    public ApiResponse<MessageDtos.CreateMessageResponse> create(
            @RequestParam(required = false) String content,
            @RequestParam String receiverId,
            @RequestPart(name = "image", required = false) MultipartFile image) {
        String imageUrl = image == null || image.isEmpty() ? "" : cloudinaryService.uploadImage(image);
        return send(content, receiverId, imageUrl);
    }

    private ApiResponse<MessageDtos.CreateMessageResponse> send(String content, String receiverId,
                                                                 String imageUrl) {
        String senderId = String.valueOf(CurrentUser.require().id());
        MessageDtos.CreateMessageResponse response = messageService.create(senderId, receiverId,
                content == null ? "" : content, imageUrl);

        // Đẩy tới destination riêng của người nhận; chỉ chính chủ subscribe được vào đó
        realtimeGateway.emit(
                RealtimeGateway.userTopic(receiverId, RealtimeGateway.CHANNEL_CHAT),
                new MessageDtos.ChatMessageEvent(response, senderId));

        return ApiResponse.created("Create message successfully", response);
    }
}
