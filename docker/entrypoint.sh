#!/bin/bash
# Điểm vào của container: dựng cấu hình nginx theo $PORT, chờ app sẵn sàng, bật nginx, và giao
# tiến trình chính cho JVM.
#
# Phân vai cổng:
#   $PORT (Render cấp lúc chạy)  -> nginx, cổng công khai DUY NHẤT
#   127.0.0.1:$REST_PORT         -> Tomcat, REST API      (mặc định 8080)
#   127.0.0.1:$SOCK_PORT         -> netty-socketio        (mặc định 8081)
#
# Hai cổng nội bộ KHÔNG đọc từ biến môi trường: chúng không bao giờ lộ ra ngoài container nên
# không có gì để mà cấu hình. Nếu Render Environment còn APP_PORT/SOCKET_PORT/SOCKET_HOSTNAME
# sót lại từ lần deploy trước thì xoá đi cho đỡ hiểu nhầm — tham số dòng lệnh bên dưới có
# precedence cao nhất trong Spring nên các biến đó có tồn tại cũng vô tác dụng.
set -e

PORT="${PORT:-10000}"

# Cổng nội bộ mặc định. Chỉ đổi khi $PORT đâm trúng chúng — nếu ai đó khai tay PORT=8080 trên
# Render (chuyện hoàn toàn hợp lệ, bản trước lớp proxy này chạy đúng ở cổng đó) thì dời cổng
# nội bộ đi, chứ không chết cả container: nginx là thứ duy nhất cần nghe đúng $PORT.
REST_PORT=8080
SOCK_PORT=8081
if [ "$PORT" = "$REST_PORT" ] || [ "$PORT" = "$SOCK_PORT" ]; then
    REST_PORT=18080
    SOCK_PORT=18081
    echo "entrypoint: PORT=$PORT trùng cổng nội bộ mặc định, dời upstream sang $REST_PORT/$SOCK_PORT"
fi
export PORT REST_PORT SOCK_PORT

# envsubst PHẢI được chỉ định đúng danh sách biến. Bỏ trống tham số thì nó nuốt luôn
# $http_upgrade, $host, $remote_addr... của nginx — xem chú thích trong nginx.conf.template.
envsubst '${PORT} ${REST_PORT} ${SOCK_PORT}' < /app/nginx.conf.template > /app/runtime/nginx.conf

# Bắt lỗi cú pháp ngay tại đây thay vì để container chết lặng lẽ sau đó.
nginx -c /app/runtime/nginx.conf -t

# Chờ upstream sẵn sàng rồi MỚI mở cổng công khai.
#
# Spring cần ~30s để lên. Bật nginx ngay lập tức nghĩa là $PORT mở trong khi phía sau chưa có
# ai — Render thấy cổng đã mở liền coi như deploy xong và cho traffic vào, health check đập
# trúng 502 và deploy bị đánh trượt. Bản trước lớp proxy này không có vấn đề đó vì chính JVM
# giữ cổng: cổng mở ĐỒNG NGHĨA app đã sẵn sàng. Vòng chờ dưới đây khôi phục lại đúng ngữ nghĩa
# đó. Kèm theo, không còn cửa sổ 30s trả 502 cho frontend — mà 502 do nginx sinh ra thì không
# có header CORS, nên trình duyệt báo thành lỗi "No 'Access-Control-Allow-Origin' header",
# che mất nguyên nhân thật.
#
# Dùng /dev/tcp của bash (ảnh debian-slim không có nc/curl) nên script này chạy bằng bash.
READY_TIMEOUT_SECONDS=300

wait_for_port() {
    local port="$1" name="$2" waited=0
    until (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null; do
        sleep 0.5
        waited=$((waited + 1))
        if [ "$waited" -ge $((READY_TIMEOUT_SECONDS * 2)) ]; then
            echo "entrypoint: $name (cổng $port) không lên sau ${READY_TIMEOUT_SECONDS}s, bật nginx để lỗi còn nhìn thấy được" >&2
            return 1
        fi
    done
    exec 3>&-
    echo "entrypoint: $name sẵn sàng ở cổng $port"
}

# Chạy nền để JVM chiếm được PID 1 ngay bên dưới. Sau `exec`, tiến trình con này được java nhận
# làm con nuôi; nó thoát ngay sau khi bật nginx và để lại một mục <defunct> vì JVM không thu
# hồi tiến trình con — vô hại, và đổi lại thì SIGTERM của Render vẫn tới thẳng java.
{
    wait_for_port "$REST_PORT" "Tomcat (REST)" || true
    wait_for_port "$SOCK_PORT" "Socket.IO" || true
    echo "entrypoint: mở cổng công khai $PORT -> 127.0.0.1:$REST_PORT (REST) và 127.0.0.1:$SOCK_PORT (Socket.IO)"
    nginx -c /app/runtime/nginx.conf
} &

# JVM giữ vai PID 1 để nhận SIGTERM của Render và tắt có trật tự (đóng pool, flush log).
#
# Truyền cổng bằng tham số dòng lệnh vì nó ghi đè mọi nguồn khác trong Spring, kể cả
# `server.port: ${APP_PORT}` và `lopet.socket.port: ${SOCKET_PORT}` trong application.yml.
# Bind vào 127.0.0.1: chỉ nginx cùng container mới gọi được, không có đường nào từ ngoài
# chạm thẳng vào Tomcat hay Socket.IO.
echo "entrypoint: PORT=$PORT, upstream REST=$REST_PORT, upstream Socket.IO=$SOCK_PORT"
exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher \
    --server.address=127.0.0.1 \
    --server.port="$REST_PORT" \
    --lopet.socket.hostname=127.0.0.1 \
    --lopet.socket.port="$SOCK_PORT"
