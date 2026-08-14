package com.nguyenvu.lopet.message;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.nguyenvu.lopet.message.dto.MessageDtos;
import com.nguyenvu.lopet.realtime.RealtimeGateway;

import lombok.RequiredArgsConstructor;

/**
 * Đẩy sự kiện {@code message status} về NGƯỜI GỬI của từng tin vừa đổi trạng thái.
 *
 * <p>Gọi từ bên NGOÀI transaction của service, không phải bên trong: xem
 * {@link MessageDtos.StatusUpdateResult}.
 *
 * <p>Một lần đánh dấu đã xem có thể chạm tới tin của nhiều người khác nhau (hội thoại hai chiều),
 * nên phải gộp theo người gửi rồi mới gửi — mỗi người nhận đúng danh sách id của mình và không thấy
 * id tin nhắn của người kia.
 */
@Component
@RequiredArgsConstructor
public class MessageStatusNotifier {

    private final RealtimeGateway realtimeGateway;

    public void broadcast(MessageDtos.StatusUpdateResult result) {
        if (result.isEmpty()) {
            return;
        }
        Map<Integer, List<Integer>> theoNguoiGui = result.changes().stream()
                .collect(Collectors.groupingBy(MessageDtos.StatusChange::senderId,
                        Collectors.mapping(MessageDtos.StatusChange::messageId, Collectors.toList())));

        theoNguoiGui.forEach((senderId, messageIds) -> realtimeGateway.emit(
                RealtimeGateway.userRoom(senderId),
                RealtimeGateway.EVENT_MESSAGE_STATUS,
                new MessageDtos.MessageStatusEvent(messageIds, result.status(), result.at(),
                        result.byUserId())));
    }
}
