package com.nguyenvu.lopet.realtime;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;

/**
 * Điểm mở rộng để module nghiệp vụ đăng ký handler cho sự kiện socket ĐẾN mà không phải sửa
 * {@link SocketIoServerRunner}.
 *
 * <p>Nếu không có lớp này, runner phải inject service của từng module — realtime sẽ phụ thuộc
 * ngược vào message, notification... trong khi cả codebase đang đi một chiều nghiệp vụ → realtime.
 *
 * <p>Mọi implementation đăng ký sự kiện làm THAY ĐỔI dữ liệu đều phải lấy danh tính bằng
 * {@link #authenticatedUserId(SocketIOClient)} và bỏ qua khi null. Kết nối chưa xác thực vẫn sống
 * được vài giây tới lúc đồng hồ dọn dẹp của runner chạy, và trong khoảng đó nó gửi được sự kiện
 * bất kỳ — bỏ kiểm tra này là mở đường cho người lạ đổi trạng thái tin nhắn của người khác.
 */
public interface SocketEventRegistrar {

    void register(SocketIOServer server);

    /** {@code null} khi kết nối chưa qua {@code AuthTokenListener} — xem {@link SocketIoConfig} */
    default Integer authenticatedUserId(SocketIOClient client) {
        Object userId = client.get(SocketIoConfig.USER_ID_KEY);
        return userId instanceof Integer id ? id : null;
    }
}
