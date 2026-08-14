# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Tải dependency ở layer riêng: sửa code thì bước này trúng cache, chỉ khi pom.xml đổi mới tải lại.
COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src ./src

# Bỏ test khi đóng gói: test tích hợp cần MySQL/Redis của docker-compose, thứ không tồn tại trên
# máy build của Render. Test chạy ở CI/máy dev, không phải ở bước build image.
RUN mvn -B -DskipTests package

# ---------- Stage 2: runtime ----------
# JRE full của Temurin: ảnh nặng hơn bản jlink nhưng không phải liệt kê tay module nào cả, nên
# không có rủi ro thiếu module chỉ nạp qua reflection (JDBC driver, cipher suite TLS, SASL...).
FROM eclipse-temurin:21-jre

# nginx-light: bản rút gọn, đã có sẵn ngx_http_proxy_module và ngx_http_map_module — đủ cho một
# reverse proxy thuần. gettext-base: lấy `envsubst` để bơm $PORT vào nginx.conf lúc khởi động,
# vì nginx không tự đọc biến môi trường trong file cấu hình.
RUN apt-get update \
 && apt-get install -y --no-install-recommends nginx-light gettext-base \
 && rm -rf /var/lib/apt/lists/*

# LocalDateTime.now() lấy theo múi giờ hệ thống. Container mặc định UTC, không đặt biến này thì
# createdAt/grantedAt/hạn OTP lệch 7 tiếng so với dữ liệu đang có.
ENV TZ=Asia/Ho_Chi_Minh

# MaxRAMPercentage thay cho -Xmx cố định: JVM tự co giãn theo RAM Render cấp cho instance.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# Không chạy bằng root.
RUN useradd --system --create-home --shell /usr/sbin/nologin lopet
WORKDIR /app

COPY --from=build /build/target/lopet-0.0.1-SNAPSHOT.jar /app/app.jar

# Hạ tầng proxy: nginx gộp hai cổng nội bộ (REST + Socket.IO) về đúng một cổng công khai $PORT,
# thứ duy nhất Render định tuyến tới.
COPY docker/nginx.conf.template /app/nginx.conf.template
COPY docker/entrypoint.sh /app/entrypoint.sh

# nginx của gói Debian/Ubuntu mặc định ghi pid vào /run và file tạm vào /var/lib/nginx — cả hai
# đều ngoài tầm với của user lopet. Dồn hết vào /app/runtime, thư mục duy nhất process ghi được.
# Bit thực thi đặt ở đây chứ không trông vào quyền của file trên máy build: repo này clone trên
# Windows, nơi NTFS không giữ chmod nào cả.
RUN mkdir -p /app/runtime \
 && chown lopet:lopet /app/runtime \
 && chmod +x /app/entrypoint.sh

USER lopet

# Chỉ để ghi tài liệu; Render định tuyến theo $PORT chứ không đọc EXPOSE. Cổng thật do nginx mở,
# lấy từ $PORT lúc chạy; 8080/8081 chỉ sống bên trong container.
EXPOSE 10000

ENTRYPOINT ["/app/entrypoint.sh"]
