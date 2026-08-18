package com.nguyenvu.lopet.realtime;

import org.springframework.stereotype.Component;

import com.corundumstudio.socketio.SocketIOClient;
import com.nguyenvu.lopet.security.petcontext.PetOwnerResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bản đối xứng của {@code PetContextInterceptor} cho kênh Socket.IO.
 *
 * <p>Không dùng lại được interceptor: handler socket chạy trên event loop của Netty, không phải
 * trong một HTTP request, nên không có header, không có {@code RequestContextHolder}, và
 * {@code SecurityContextHolder} thì rỗng. Nhưng QUY TẮC phải giống hệt — nếu không, kênh socket sẽ
 * là đường vòng qua toàn bộ tầng phân quyền của kênh REST.
 *
 * <p>Client gửi {@code petId} kèm payload của sự kiện; accountId thì KHÔNG lấy từ payload mà lấy từ
 * {@code SocketIoConfig.USER_ID_KEY} — giá trị do server gắn vào sau khi kiểm JWT lúc handshake.
 * Đó là điều kiện để "gửi petId của người khác" không trở thành cách đóng giả họ.
 *
 * <p>Dùng chung {@link PetOwnerResolver} với kênh REST, nên cùng cache Redis và cùng bị vô hiệu hoá
 * bởi cùng một lời gọi {@code invalidate}. Hai bộ nhớ đệm riêng sẽ hết hạn lệch nhau và cho ra hai
 * câu trả lời khác nhau cho cùng một câu hỏi.
 *
 * <p><b>Chưa handler nào gọi tới đây.</b> Hai sự kiện đến hiện có ({@code message delivered},
 * {@code message read}) thao tác trên tin nhắn, mà tin nhắn vẫn thuộc về TÀI KHOẢN chứ không phải
 * pet. Lớp này tồn tại sẵn cho các sự kiện nhân danh pet sẽ tới cùng đợt đổi khoá ngoại của nội dung
 * xã hội — viết nó cùng lúc với kênh REST là cách duy nhất đảm bảo hai kênh không trôi khác nhau.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocketPetContext {

    private final PetOwnerResolver petOwnerResolver;

    /**
     * @return petId đã xác nhận thuộc về người đang kết nối, hoặc {@code null} nếu không hợp lệ —
     *         handler phải bỏ qua sự kiện khi nhận null, đúng cách
     *         {@code SocketEventRegistrar.authenticatedUserId} đang được dùng.
     */
    public Integer resolve(SocketIOClient client, Integer petIdFromPayload) {
        Object accountId = client.get(SocketIoConfig.USER_ID_KEY);
        if (!(accountId instanceof Integer callerId)) {
            log.debug("Bỏ qua sự kiện từ kết nối chưa xác thực {}", client.getSessionId());
            return null;
        }
        if (petIdFromPayload == null) {
            log.debug("Sự kiện của user {} thiếu petId", callerId);
            return null;
        }

        Integer ownerId = petOwnerResolver.ownerAccountIdOf(petIdFromPayload);
        if (ownerId == null || !ownerId.equals(callerId)) {
            log.warn("User {} gửi sự kiện nhân danh pet {} không thuộc quyền sở hữu", callerId, petIdFromPayload);
            return null;
        }
        return petIdFromPayload;
    }
}
