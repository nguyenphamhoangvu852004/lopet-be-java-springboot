# syntax=docker/dockerfile:1

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Tải dependency ở layer riêng: pom.xml đổi mới phải tải lại, sửa code thì dùng cache.
COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src ./src
# Bỏ test khi đóng gói: các test tích hợp cần MySQL của docker-compose ở cổng 3307, thứ không tồn
# tại trong máy build của Render. Test chạy ở CI/máy dev, không phải ở bước build image.
RUN mvn -B -DskipTests package

# ---------- Stage 2: runtime ----------
# Bản Ubuntu chứ không phải alpine: netty (netty-socketio) dựa vào glibc, chạy trên musl là chuốc
# lấy một lớp rủi ro không cần thiết để đổi lấy vài chục MB.
FROM eclipse-temurin:21-jre AS runtime

# LocalDateTime.now() trong code lấy theo múi giờ hệ thống. Container mặc định là UTC, nên nếu
# không đặt biến này thì createdAt/grantedAt/hạn OTP trên Render lệch 7 tiếng so với dữ liệu đang
# có. Đổi giá trị này nếu muốn chuẩn hoá toàn hệ thống về UTC — nhưng phải đổi cả frontend.
ENV TZ=Asia/Ho_Chi_Minh

# MaxRAMPercentage thay cho -Xmx cố định: JVM tự co giãn theo RAM Render cấp cho instance.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# Không chạy bằng root.
RUN useradd --system --create-home --shell /usr/sbin/nologin lopet
WORKDIR /app

COPY --from=build /build/target/lopet-0.0.1-SNAPSHOT.jar /app/app.jar
RUN chown -R lopet:lopet /app
USER lopet

# Chỉ để ghi tài liệu; Render định tuyến theo $PORT chứ không đọc EXPOSE.
EXPOSE 8080

# Render cấp cổng qua $PORT và bắt app phải nghe đúng cổng đó. Truyền bằng tham số dòng lệnh vì
# nó có precedence cao nhất, ghi đè hẳn `server.port: ${APP_PORT}` trong application.yml — nhờ vậy
# không cần khai thêm biến APP_PORT trên Render.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar --server.port=${PORT:-8080}"]
