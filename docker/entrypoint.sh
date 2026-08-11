#!/bin/sh
# Điểm vào của container: dựng cấu hình nginx theo $PORT, bật nginx, rồi giao tiến trình chính
# cho JVM.
#
# Phân vai cổng:
#   $PORT (Render cấp lúc chạy)  -> nginx, cổng công khai DUY NHẤT
#   127.0.0.1:8080               -> Tomcat, REST API
#   127.0.0.1:8081               -> netty-socketio, realtime
#
# Hai cổng nội bộ cố định, KHÔNG đọc từ biến môi trường nữa: chúng không bao giờ lộ ra ngoài
# container nên không có gì để mà cấu hình. Nếu Render Environment còn APP_PORT/SOCKET_PORT/
# SOCKET_HOSTNAME sót lại từ lần deploy trước thì xoá đi cho đỡ hiểu nhầm — tham số dòng lệnh
# bên dưới có precedence cao nhất trong Spring nên các biến đó có tồn tại cũng vô tác dụng.
set -e

PORT="${PORT:-10000}"

if [ "$PORT" = "8080" ] || [ "$PORT" = "8081" ]; then
    echo "entrypoint: PORT=$PORT trùng cổng nội bộ (8080 Tomcat / 8081 Socket.IO)." >&2
    echo "entrypoint: chọn cổng khác cho -e PORT; 8080/8081 chỉ dùng bên trong container." >&2
    exit 1
fi

# envsubst PHẢI được chỉ định đúng danh sách biến. Bỏ trống tham số thì nó nuốt luôn
# $http_upgrade, $host, $remote_addr... của nginx — xem chú thích trong nginx.conf.template.
envsubst '${PORT}' < /app/nginx.conf.template > /app/runtime/nginx.conf

# Bắt lỗi cú pháp ngay tại đây thay vì để container chết lặng lẽ sau đó.
nginx -c /app/runtime/nginx.conf -t

echo "entrypoint: nginx nghe cổng $PORT -> 127.0.0.1:8080 (REST) và 127.0.0.1:8081 (Socket.IO)"
nginx -c /app/runtime/nginx.conf

# JVM giữ vai PID 1 để nhận SIGTERM của Render và tắt có trật tự (đóng pool, flush log).
#
# Truyền cổng bằng tham số dòng lệnh vì nó ghi đè mọi nguồn khác trong Spring, kể cả
# `server.port: ${APP_PORT}` và `lopet.socket.port: ${SOCKET_PORT}` trong application.yml.
# Bind vào 127.0.0.1: chỉ nginx cùng container mới gọi được, không có đường nào từ ngoài
# chạm thẳng vào Tomcat hay Socket.IO.
exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher \
    --server.address=127.0.0.1 \
    --server.port=8080 \
    --lopet.socket.hostname=127.0.0.1 \
    --lopet.socket.port=8081
