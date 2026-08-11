package com.nguyenvu.lopet.realtime;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Vòng đời của Socket.IO server và các handler tương ứng với {@code setupSocket()} bên TS:
 * cho phép client tự join phòng khác, và ghi log khi ngắt. Việc tự vào phòng riêng nằm ở
 * {@link SocketIoConfig} vì {@code ConnectListener} của netty-socketio chạy trước khi xác thực
 * xong, lúc đó chưa biết {@code userId} — xem javadoc lớp đó.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocketIoServerRunner implements SmartLifecycle {

    /**
     * Client gửi gói CONNECT (kèm {@code auth}) ngay sau khi bắt tay, thường trong vài chục ms.
     * Quá hạn này mà vẫn chưa có danh tính nghĩa là client không hề gửi {@code auth}.
     */
    private static final long AUTH_TIMEOUT_SECONDS = 5;

    private final SocketIOServer socketIOServer;

    @Value("${lopet.socket.port:8081}")
    private int port;

    private volatile boolean running;

    private final ScheduledExecutorService authWatchdog = Executors.newSingleThreadScheduledExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "socketio-auth-watchdog");
                thread.setDaemon(true);
                return thread;
            });

    @Override
    public void start() {
        // Cổng 0 = tắt hẳn Socket.IO (dùng cho test tích hợp, nơi không cần realtime)
        if (port <= 0) {
            log.info("Bỏ qua Socket.IO: lopet.socket.port = {}", port);
            return;
        }

        socketIOServer.addConnectListener(this::onConnect);
        socketIOServer.addDisconnectListener(client -> log.info("User {} disconnected", userIdOf(client)));
        socketIOServer.addEventListener("join room", String.class, (client, room, ackRequest) -> {
            // Kết nối chưa xác thực vẫn sống được tới khi đồng hồ dọn dẹp chạy, nên chặn ở đây:
            // không thì client vô danh join được phòng của người khác và nghe lén.
            if (userIdOf(client) == null) {
                log.debug("Bỏ qua 'join room' từ kết nối chưa xác thực {}", client.getSessionId());
                return;
            }
            client.joinRoom(room);
            log.info("{} joined room {}", client.getSessionId(), room);
        });

        try {
            socketIOServer.start();
        } catch (RuntimeException exception) {
            // Netty ném BindException lồng sâu trong một chuỗi stack trace không hề nhắc tới cổng
            // nào đang hỏng. SOCKET_PORT là cổng thứ hai do bản Java thêm vào (bản Express dùng
            // chung cổng HTTP), nên đây là kiểu lỗi người vận hành chưa từng gặp ở backend cũ —
            // phải nói thẳng tên biến môi trường cần sửa.
            throw new IllegalStateException(
                    "Không mở được Socket.IO trên cổng " + port + ". Cổng đang bị tiến trình khác giữ"
                            + " — đổi biến môi trường SOCKET_PORT, hoặc dừng tiến trình đang chiếm cổng đó."
                            + " Đặt SOCKET_PORT=0 để tắt hẳn realtime.", exception);
        }
        running = true;
        log.info("Socket.IO server started on port {}", port);
    }

    /**
     * Sự kiện này đến TRƯỚC khi {@code auth} được xử lý, nên ở đây chưa có {@code userId} và cũng
     * chưa kết luận được gì. Việc duy nhất làm được: hẹn giờ kiểm tra lại, ngắt các kết nối không
     * bao giờ gửi {@code auth} (với chúng, netty-socketio không gọi {@code AuthTokenListener} lần
     * nào nên không có chỗ nào khác từ chối được).
     */
    private void onConnect(SocketIOClient client) {
        // netty-socketio phát connect hai lần cho EIO4: lần sau đã có danh tính, không cần hẹn nữa
        if (userIdOf(client) != null) {
            return;
        }
        authWatchdog.schedule(() -> {
            if (userIdOf(client) == null && client.isChannelOpen()) {
                log.debug("Socket authentication failed: thiếu auth.token, ngắt {}", client.getSessionId());
                client.disconnect();
            }
        }, AUTH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private Object userIdOf(SocketIOClient client) {
        return client.get(SocketIoConfig.USER_ID_KEY);
    }

    @Override
    public void stop() {
        authWatchdog.shutdownNow();
        if (running) {
            socketIOServer.stop();
            running = false;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
