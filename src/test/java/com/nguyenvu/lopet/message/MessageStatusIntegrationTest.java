package com.nguyenvu.lopet.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nguyenvu.lopet.auth.AuthService;
import com.nguyenvu.lopet.auth.dto.RegisterRequest;
import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.email.OtpStore;
import com.nguyenvu.lopet.message.dto.MessageDtos;
import com.nguyenvu.lopet.message.entity.Message;
import com.nguyenvu.lopet.message.entity.MessageStatus;
import com.nguyenvu.lopet.message.repository.MessageRepository;
import com.nguyenvu.lopet.support.IntegrationTestBase;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Trạng thái tin nhắn")
class MessageStatusIntegrationTest extends IntegrationTestBase {

    private static final String RUN = Long.toString(System.nanoTime(), 36);

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> IntegrationTestBase.jdbcUrl("lopet_java_message_test"));
    }

    @Autowired
    private AuthService authService;
    @Autowired
    private MessageService messageService;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private OtpStore otpStore;

    private Integer register(String name) {
        String unique = name + "-" + RUN;
        String email = unique + "@message.local";
        otpStore.markVerified(email);
        return authService.register(
                new RegisterRequest(email, unique, "password123", "password123")).id();
    }

    private Integer send(Integer senderId, Integer receiverId, String content) {
        return messageService.create(String.valueOf(senderId), String.valueOf(receiverId), content, "").id();
    }

    private Message reload(Integer messageId) {
        return inTransaction(() -> messageRepository.findDetailById(messageId).orElseThrow());
    }

    @Nested
    @DisplayName("Tạo tin nhắn")
    class TaoTinNhan {

        @Test
        @DisplayName("response mang id của bản ghi vừa lưu — không có id thì client không ack được")
        void responseCoId() {
            Integer nguoiGui = register("create-sender");
            Integer nguoiNhan = register("create-receiver");

            MessageDtos.CreateMessageResponse response = messageService.create(
                    String.valueOf(nguoiGui), String.valueOf(nguoiNhan), "xin chào", "");

            assertThat(response.id()).isNotNull();
            assertThat(response.status()).isEqualTo(MessageStatus.SENT);
            assertThat(response.createdAt()).isNotNull();
            assertThat(response.senderId()).isEqualTo(String.valueOf(nguoiGui));
            assertThat(response.receiverId()).isEqualTo(String.valueOf(nguoiNhan));
        }

        @Test
        @DisplayName("tin mới chưa có mốc nhận lẫn mốc xem")
        void tinMoiChuaCoMoc() {
            Integer nguoiGui = register("fresh-sender");
            Integer nguoiNhan = register("fresh-receiver");

            Message message = reload(send(nguoiGui, nguoiNhan, "tin mới"));

            assertThat(message.getStatus()).isEqualTo(MessageStatus.SENT);
            assertThat(message.getDeliveredAt()).isNull();
            assertThat(message.getReadAt()).isNull();
        }
    }

    @Nested
    @DisplayName("Ack đã nhận")
    class DaNhan {

        @Test
        @DisplayName("đánh dấu cả lô và trả về đúng người gửi cần được báo")
        void danhDauCaLo() {
            Integer nguoiGui = register("ack-sender");
            Integer nguoiNhan = register("ack-receiver");
            Integer mot = send(nguoiGui, nguoiNhan, "một");
            Integer hai = send(nguoiGui, nguoiNhan, "hai");

            MessageDtos.StatusUpdateResult ketQua = messageService.markDelivered(nguoiNhan, List.of(mot, hai));

            assertThat(ketQua.count()).isEqualTo(2);
            assertThat(ketQua.status()).isEqualTo(MessageStatus.DELIVERED);
            assertThat(ketQua.changes()).extracting(MessageDtos.StatusChange::senderId)
                    .containsOnly(nguoiGui);
            assertThat(reload(mot).getDeliveredAt()).isNotNull();
            assertThat(reload(hai).getStatus()).isEqualTo(MessageStatus.DELIVERED);
        }

        @Test
        @DisplayName("id của tin gửi cho người khác bị bỏ qua, không ném lỗi")
        void khongDungDuocTinCuaNguoiKhac() {
            Integer nguoiGui = register("intruder-sender");
            Integer nanNhan = register("intruder-victim");
            Integer keLa = register("intruder-stranger");
            Integer cuaNanNhan = send(nguoiGui, nanNhan, "tin riêng");

            MessageDtos.StatusUpdateResult ketQua = messageService.markDelivered(keLa, List.of(cuaNanNhan));

            assertThat(ketQua.isEmpty()).isTrue();
            assertThat(reload(cuaNanNhan).getStatus()).isEqualTo(MessageStatus.SENT);
            assertThat(reload(cuaNanNhan).getDeliveredAt()).isNull();
        }

        @Test
        @DisplayName("ack lặp lại không dời mốc nhận về hiện tại")
        void ackLapLaiGiuMocCu() {
            Integer nguoiGui = register("repeat-sender");
            Integer nguoiNhan = register("repeat-receiver");
            Integer id = send(nguoiGui, nguoiNhan, "gửi lại sau reconnect");
            messageService.markDelivered(nguoiNhan, List.of(id));
            var mocDau = reload(id).getDeliveredAt();

            MessageDtos.StatusUpdateResult lanHai = messageService.markDelivered(nguoiNhan, List.of(id));

            assertThat(lanHai.isEmpty()).as("tin đã DELIVERED không còn là ứng viên").isTrue();
            assertThat(reload(id).getDeliveredAt()).isEqualTo(mocDau);
        }

        @Test
        @DisplayName("lô rỗng không chạm database")
        void loRong() {
            Integer nguoiNhan = register("empty-ack");

            assertThat(messageService.markDelivered(nguoiNhan, List.of()).isEmpty()).isTrue();
            assertThat(messageService.markDelivered(nguoiNhan, null).isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("Đánh dấu đã xem cả hội thoại")
    class DaXem {

        @Test
        @DisplayName("một lần gọi đánh dấu hết tin chưa đọc của đúng hội thoại đó")
        void danhDauCaHoiThoai() {
            Integer doiPhuong = register("read-partner");
            Integer toi = register("read-me");
            Integer nguoiKhac = register("read-other");
            Integer mot = send(doiPhuong, toi, "một");
            Integer hai = send(doiPhuong, toi, "hai");
            Integer cuaNguoiKhac = send(nguoiKhac, toi, "hội thoại khác");

            MessageDtos.StatusUpdateResult ketQua = messageService.markConversationRead(toi, doiPhuong);

            assertThat(ketQua.count()).isEqualTo(2);
            assertThat(ketQua.changes()).extracting(MessageDtos.StatusChange::messageId)
                    .containsExactlyInAnyOrder(mot, hai);
            assertThat(reload(cuaNguoiKhac).getStatus())
                    .as("hội thoại khác không được đụng tới").isEqualTo(MessageStatus.SENT);
        }

        @Test
        @DisplayName("đã xem thì đương nhiên đã nhận — deliveredAt được điền luôn")
        void daXemKeoTheoDaNhan() {
            Integer doiPhuong = register("jump-partner");
            Integer toi = register("jump-me");
            Integer id = send(doiPhuong, toi, "mở app là thấy");

            messageService.markConversationRead(toi, doiPhuong);

            Message sau = reload(id);
            assertThat(sau.getStatus()).isEqualTo(MessageStatus.READ);
            assertThat(sau.getReadAt()).isNotNull();
            assertThat(sau.getDeliveredAt()).isNotNull();
        }

        @Test
        @DisplayName("tin do chính mình gửi không bao giờ bị đánh dấu đã xem")
        void khongDanhDauTinCuaChinhMinh() {
            Integer toi = register("own-me");
            Integer doiPhuong = register("own-partner");
            Integer cuaToi = send(toi, doiPhuong, "tin tôi gửi đi");

            messageService.markConversationRead(toi, doiPhuong);

            assertThat(reload(cuaToi).getStatus()).isEqualTo(MessageStatus.SENT);
        }
    }

    @Nested
    @DisplayName("Đổi trạng thái một tin")
    class DoiMotTin {

        @Test
        @DisplayName("người gửi KHÔNG đổi được trạng thái tin của chính mình")
        void nguoiGuiKhongDoiDuoc() {
            Integer nguoiGui = register("forge-sender");
            Integer nguoiNhan = register("forge-receiver");
            Integer id = send(nguoiGui, nguoiNhan, "tin sắp bị giả dấu đã xem");

            assertThatThrownBy(() -> messageService.changeStatus(nguoiGui, id, MessageStatus.READ))
                    .isInstanceOf(ForbiddenException.class);
            assertThat(reload(id).getStatus()).isEqualTo(MessageStatus.SENT);
        }

        @Test
        @DisplayName("không lùi được trạng thái, và lùi thì im lặng chứ không báo lỗi")
        void khongLuiDuocTrangThai() {
            Integer nguoiGui = register("downgrade-sender");
            Integer nguoiNhan = register("downgrade-receiver");
            Integer id = send(nguoiGui, nguoiNhan, "đã xem rồi");
            messageService.changeStatus(nguoiNhan, id, MessageStatus.READ);

            MessageDtos.StatusUpdateResult lui =
                    messageService.changeStatus(nguoiNhan, id, MessageStatus.DELIVERED);

            assertThat(lui.isEmpty()).isTrue();
            assertThat(reload(id).getStatus()).isEqualTo(MessageStatus.READ);
        }

        @Test
        @DisplayName("người nhận tiến trạng thái bình thường")
        void nguoiNhanTienTrangThai() {
            Integer nguoiGui = register("advance-sender");
            Integer nguoiNhan = register("advance-receiver");
            Integer id = send(nguoiGui, nguoiNhan, "tin thường");

            MessageDtos.StatusUpdateResult ketQua =
                    messageService.changeStatus(nguoiNhan, id, MessageStatus.DELIVERED);

            assertThat(ketQua.count()).isEqualTo(1);
            assertThat(ketQua.changes().getFirst().senderId()).isEqualTo(nguoiGui);
            assertThat(reload(id).getStatus()).isEqualTo(MessageStatus.DELIVERED);
        }
    }

    @Nested
    @DisplayName("Đếm tin chưa đọc")
    class DemChuaDoc {

        @Test
        @DisplayName("đếm mọi tin chưa READ gửi tới mình, bất kể của ai")
        void demTinChuaDoc() {
            Integer toi = register("count-me");
            Integer motNguoi = register("count-a");
            Integer nguoiKhac = register("count-b");
            send(motNguoi, toi, "một");
            send(nguoiKhac, toi, "hai");
            Integer daXem = send(motNguoi, toi, "ba");
            send(toi, motNguoi, "tin tôi gửi đi, không tính");

            messageService.changeStatus(toi, daXem, MessageStatus.READ);

            assertThat(messageService.countUnread(toi)).isEqualTo(2);
        }
    }
}
