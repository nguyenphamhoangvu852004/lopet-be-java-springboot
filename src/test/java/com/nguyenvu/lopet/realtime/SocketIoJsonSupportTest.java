package com.nguyenvu.lopet.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.corundumstudio.socketio.namespace.Namespace;
import com.nguyenvu.lopet.config.JacksonConfig;
import com.nguyenvu.lopet.message.dto.MessageDtos;
import com.nguyenvu.lopet.message.entity.MessageStatus;
import com.nguyenvu.lopet.notification.dto.NotificationDtos;
import com.nguyenvu.lopet.notification.entity.NotificationObjectType;
import com.nguyenvu.lopet.notification.entity.NotificationStatus;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;

/**
 * netty-socketio serialize bằng Jackson 2 của riêng nó, tách rời ObjectMapper (Jackson 3) mà Spring
 * cấu hình cho REST. Hai sai lệch từng xảy ra thật ở đây và cùng im lặng với người dùng — lỗi chỉ
 * hiện trong log của event loop, REST vẫn trả 201:
 * <ul>
 *   <li>không có handler cho {@code LocalDateTime} → sự kiện chết lúc encode, client không nhận gì;</li>
 *   <li>có handler nhưng khác định dạng REST → cùng một mốc thời gian hiện hai kiểu trên giao diện.</li>
 * </ul>
 */
class SocketIoJsonSupportTest {

    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(2026, 8, 10, 17, 58, 44, 122_797_000);

    @Test
    void su_kien_notification_serialize_duoc_va_dung_dinh_dang_ngay_cua_REST() throws Exception {
        NotificationDtos.NotificationEvent event = new NotificationDtos.NotificationEvent(
                9, 2, 1, "nphvudev đã bình luận bài viết của bạn",
                NotificationObjectType.POST, 5, NotificationStatus.SENT, CREATED_AT);

        assertThat(serialize(List.of("notification", event)))
                .contains("\"createdAt\":\"" + JacksonConfig.nodeIso(CREATED_AT) + "\"")
                .contains("\"objectType\":\"POST\"")
                .contains("\"notificationId\":9")
                .contains("\"objectId\":5");
    }

    @Test
    void su_kien_change_status_cung_mang_LocalDateTime() throws Exception {
        NotificationDtos.UpdateNotificationResponse response =
                new NotificationDtos.UpdateNotificationResponse(7, 2, 1, "nội dung", CREATED_AT,
                        NotificationStatus.READ, NotificationObjectType.POST, 5);

        assertThat(serialize(List.of("change status", response)))
                .contains("\"createdAt\":\"" + JacksonConfig.nodeIso(CREATED_AT) + "\"");
    }

    @Test
    void su_kien_message_status_mang_ca_danh_sach_id_lan_moc_thoi_gian() throws Exception {
        MessageDtos.MessageStatusEvent event = new MessageDtos.MessageStatusEvent(
                List.of(11, 12), MessageStatus.READ, CREATED_AT, 4);

        assertThat(serialize(List.of("message status", event)))
                .contains("\"messageIds\":[11,12]")
                .contains("\"status\":\"READ\"")
                .contains("\"at\":\"" + JacksonConfig.nodeIso(CREATED_AT) + "\"")
                .contains("\"byUserId\":4");
    }

    /**
     * Chiều NGƯỢC LẠI — payload client gửi lên. Đây là lý do {@code DeliveredAckRequest} là class
     * thường chứ không phải record: ObjectMapper của netty-socketio không được nạp module
     * {@code parameter-names}, nên record chỉ deserialize được nhờ hỗ trợ sẵn của Jackson. Test này
     * chốt việc payload dựng được thật, thay vì tin vào phiên bản Jackson mà netty-socketio kéo theo.
     */
    @Test
    void ack_da_nhan_tu_client_deserialize_duoc() throws Exception {
        MessageDtos.DeliveredAckRequest request =
                deserialize("{\"messageIds\":[7,8,9]}", MessageDtos.DeliveredAckRequest.class);

        assertThat(request.getMessageIds()).containsExactly(7, 8, 9);
    }

    @Test
    void ack_da_xem_tu_client_deserialize_duoc() throws Exception {
        MessageDtos.ReadAckRequest request =
                deserialize("{\"partnerId\":42}", MessageDtos.ReadAckRequest.class);

        assertThat(request.getPartnerId()).isEqualTo(42);
    }

    private <T> T deserialize(String json, Class<T> type) throws Exception {
        ByteBuf buffer = Unpooled.copiedBuffer(json, StandardCharsets.UTF_8);
        try (ByteBufInputStream in = new ByteBufInputStream(buffer)) {
            return SocketIoConfig.socketJsonSupport().readValue(Namespace.DEFAULT_NAME, in, type);
        } finally {
            buffer.release();
        }
    }

    private String serialize(Object value) throws Exception {
        ByteBuf buffer = Unpooled.buffer();
        try (ByteBufOutputStream out = new ByteBufOutputStream(buffer)) {
            SocketIoConfig.socketJsonSupport().writeValue(out, value);
            return buffer.toString(StandardCharsets.UTF_8);
        } finally {
            buffer.release();
        }
    }
}
