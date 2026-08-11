# syntax=docker/dockerfile:1

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Tải dependency ở layer riêng: pom.xml đổi mới phải tải lại, sửa code thì dùng cache.
# Cache mount giữ ~/.m2 ở ngoài image nên repo Maven (vài trăm MB) không bao giờ nằm trong
# layer nào cả — build stage bị vứt đi hoàn toàn, nhưng cache thì còn cho lần build sau.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline

COPY src ./src
# Bỏ test khi đóng gói: các test tích hợp cần MySQL của docker-compose ở cổng 3307, thứ không tồn
# tại trong máy build của Render. Test chạy ở CI/máy dev, không phải ở bước build image.
#
# Sau khi đóng gói thì bung fat jar thành từng layer (dependencies / spring-boot-loader /
# snapshot-dependencies / application). Tổng dung lượng không đổi, nhưng 74MB thư viện tách khỏi
# 1.5MB code của mình: sửa code rồi deploy lại thì Render chỉ phải đẩy và kéo đúng 1.5MB đó,
# ba layer kia trúng cache. Spring Boot 4 dùng `-Djarmode=tools`, `layertools` đã bị bỏ.
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package \
 && java -Djarmode=tools -jar target/lopet-0.0.1-SNAPSHOT.jar \
      extract --layers --launcher --destination /build/extracted

# ---------- Stage 2: cắt JRE bằng jlink ----------
# eclipse-temurin:21-jre là ảnh Ubuntu đầy đủ, một mình nó đã ~630MB. jlink dựng một runtime chỉ
# gồm những module thật sự dùng tới, còn khoảng 60MB. Danh sách dưới đây liệt kê tay chứ không
# nhờ jdeps: jdeps đọc bytecode nên không thấy được các module chỉ được nạp qua reflection
# (JDBC driver, cipher suite TLS, SASL...), tự động hoá ở đây là tự chuốc lỗi lúc chạy thật.
FROM eclipse-temurin:21-jdk AS jre-build

RUN "$JAVA_HOME/bin/jlink" \
      --add-modules \
java.base,\
java.compiler,\
java.desktop,\
java.instrument,\
java.logging,\
java.management,\
java.management.rmi,\
java.naming,\
java.net.http,\
java.prefs,\
java.rmi,\
java.scripting,\
java.security.jgss,\
java.security.sasl,\
java.sql,\
java.sql.rowset,\
java.transaction.xa,\
java.xml,\
java.xml.crypto,\
jdk.charsets,\
jdk.crypto.cryptoki,\
jdk.crypto.ec,\
jdk.jfr,\
jdk.localedata,\
jdk.management,\
jdk.naming.dns,\
jdk.security.auth,\
jdk.unsupported,\
jdk.zipfs \
      --include-locales=en,vi \
      --strip-debug \
      --no-man-pages \
      --no-header-files \
      --compress=zip-6 \
      --output /javaruntime

# Ghi chú vài module dễ bị tưởng là thừa:
#   java.desktop      java.beans.Introspector — Spring dùng để nội suy property của bean.
#   jdk.unsupported   sun.misc.Unsafe — netty (netty-socketio) không chạy nếu thiếu.
#   jdk.zipfs         Spring Boot loader đọc jar lồng nhau qua ZipFileSystem.
#   jdk.crypto.ec     cipher suite ECDHE, tức là mọi kết nối HTTPS/TLS ra ngoài (Resend, Cloudinary).
#   java.security.sasl mysql-connector-j và lettuce xác thực qua SASL.
#   jdk.naming.dns    netty resolver phân giải tên miền.
#   --strip-debug     chỉ bỏ debug info của class trong JDK; class của app nằm ngoài jlink nên
#                     stack trace phần code mình viết vẫn còn số dòng đầy đủ.

# ---------- Stage 3: runtime ----------
# Debian slim (~30MB) thay cho ảnh Ubuntu của temurin. Vẫn là glibc chứ không phải alpine/musl:
# netty (netty-socketio) dựa vào glibc, chạy trên musl là chuốc lấy một lớp rủi ro không cần
# thiết. Chứng chỉ TLS đi theo cacerts trong chính runtime jlink dựng ra, không cần cài thêm.
FROM debian:trixie-slim AS runtime

ENV JAVA_HOME=/opt/java
ENV PATH="$JAVA_HOME/bin:$PATH"

# LocalDateTime.now() trong code lấy theo múi giờ hệ thống. Container mặc định là UTC, nên nếu
# không đặt biến này thì createdAt/grantedAt/hạn OTP trên Render lệch 7 tiếng so với dữ liệu đang
# có. Đổi giá trị này nếu muốn chuẩn hoá toàn hệ thống về UTC — nhưng phải đổi cả frontend.
ENV TZ=Asia/Ho_Chi_Minh

# MaxRAMPercentage thay cho -Xmx cố định: JVM tự co giãn theo RAM Render cấp cho instance.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

COPY --from=jre-build /javaruntime $JAVA_HOME

# Không chạy bằng root.
RUN useradd --system --create-home --shell /usr/sbin/nologin lopet
WORKDIR /app

# Thứ tự copy đi từ ít đổi tới hay đổi, để cache layer có tác dụng: thư viện gần như đứng yên
# giữa các lần deploy, còn `application/` thì lần build nào cũng khác.
# Giữ nguyên chủ sở hữu root: process chạy bằng user lopet chỉ cần quyền đọc, không được phép
# sửa chính code của mình. Bản cũ `RUN chown -R` nhân đôi cả 77MB jar thành một layer thừa.
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

USER lopet

# Chỉ để ghi tài liệu; Render định tuyến theo $PORT chứ không đọc EXPOSE.
EXPOSE 8080

# Render cấp cổng qua $PORT và bắt app phải nghe đúng cổng đó. Truyền bằng tham số dòng lệnh vì
# nó có precedence cao nhất, ghi đè hẳn `server.port: ${APP_PORT}` trong application.yml — nhờ vậy
# không cần khai thêm biến APP_PORT trên Render.
# Chạy thẳng JarLauncher trên cây thư mục đã bung, không còn `-jar app.jar` nữa.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher --server.port=${PORT:-8080}"]
