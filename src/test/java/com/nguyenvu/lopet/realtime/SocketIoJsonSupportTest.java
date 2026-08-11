package com.nguyenvu.lopet.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.nguyenvu.lopet.config.JacksonConfig;
import com.nguyenvu.lopet.notification.dto.NotificationDtos;
import com.nguyenvu.lopet.notification.entity.NotificationObjectType;
import com.nguyenvu.lopet.notification.entity.NotificationStatus;

import io.netty.buffer.ByteBuf;
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
                2, 1, "nphvudev đã bình luận bài viết của bạn",
                NotificationObjectType.POST, NotificationStatus.SENT, CREATED_AT);

        assertThat(serialize(List.of("notification", event)))
                .contains("\"createdAt\":\"" + JacksonConfig.nodeIso(CREATED_AT) + "\"")
                .contains("\"objectType\":\"POST\"");
    }

    @Test
    void su_kien_change_status_cung_mang_LocalDateTime() throws Exception {
        NotificationDtos.UpdateNotificationResponse response =
                new NotificationDtos.UpdateNotificationResponse(7, 2, 1, "nội dung", CREATED_AT,
                        NotificationStatus.READ, NotificationObjectType.POST);

        assertThat(serialize(List.of("change status", response)))
                .contains("\"createdAt\":\"" + JacksonConfig.nodeIso(CREATED_AT) + "\"");
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
