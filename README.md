# lopet-be-java-springboot

Bản migrate Java/Spring Boot của [`lopet-be`](../lopet-be) (TypeScript + ExpressJS + TypeORM).

Mục tiêu: **thay thế trực tiếp** backend cũ — dùng lại đúng database, đúng file `.env`, và không
buộc frontend sửa gì ngoài URL của Socket.IO (xem [Khác biệt duy nhất](#khác-biệt-duy-nhất-với-bản-cũ)).

`lopet-be` là **source of truth**. Ở đâu hành vi của nó khác với best practice hay khác với cách một
mạng xã hội "nên" hoạt động, bản Java giữ theo `lopet-be`.

---

## Tình trạng

| Hạng mục | Kết quả |
|---|---|
| Endpoint | 72/72 đường dẫn, 0 thiếu |
| Sự kiện Socket.IO | 7/7 |
| Schema database | 145/145 cột và 27/27 khoá ngoại **trùng khớp** với schema do TypeORM sinh ra |
| Test | 71 test, pass 100% |

Chi tiết đối chiếu: [`docs/MIGRATION_FINAL_REPORT.md`](docs/MIGRATION_FINAL_REPORT.md).

## Công nghệ

| | `lopet-be` | dự án này |
|---|---|---|
| Runtime | Node.js | Java 21 |
| Framework | Express 5 | Spring Boot 4.1 (`spring-boot-starter-webmvc`) |
| ORM | TypeORM | Spring Data JPA / Hibernate 7 |
| Database | MySQL 8 | MySQL 8 — **cùng schema** |
| Cache/OTP | redis | Spring Data Redis |
| Auth | jsonwebtoken | JWT HS256 tự hiện thực (lý do ở báo cáo §4.3) + Spring Security |
| Hash | bcryptjs cost 10 | `BCryptPasswordEncoder(10)` — hash cũ dùng lại được |
| Validation | Joi | Bean Validation, giữ nguyên văn thông điệp lỗi |
| Realtime | socket.io 4 | netty-socketio (cùng protocol EIO4) |
| Upload | multer + Cloudinary | `MultipartFile` + Cloudinary SDK |
| Test | Jest + supertest | JUnit 5 + Mockito + MySQL thật |

## Chạy dự án

### 1. Hạ tầng

Dùng lại `docker-compose.yaml` của `lopet-be` (MySQL cổng 3307, Redis 6379):

```bash
cd ../lopet-be && docker compose up -d mysql-docker redis
```

### 2. Chạy backend

```bash
mvn package -DskipTests
java -jar target/lopet-0.0.1-SNAPSHOT.jar
```

Biến môi trường trùng tên với `example.env` của `lopet-be`, nên `.env` cũ dùng lại được:

```bash
DATABASE_HOSTNAME=127.0.0.1 DATABASE_PORT=3307 DATABASE_USERNAME=root \
DATABASE_PASSWORD=nguyenvu DATABASE_NAME=socialmedia \
REDIS_HOSTNAME=127.0.0.1 REDIS_PORT=6379 \
APP_PORT=8080 SOCKET_PORT=8081 \
ACCESS_TOKEN_SECRET=... REFRESH_TOKEN_SECRET=... \
CLOUDINARY_CLOUD_NAME=... CLOUDINARY_API_KEY=... CLOUDINARY_API_SECRET=... \
RESEND_API_KEY=re_... MAIL_FROM='Lopet <no-reply@domain-da-verify>' \
INIT_ADMIN_EMAIL=... INIT_ADMIN_USERNAME=... INIT_ADMIN_PASSWORD=... \
java -jar target/lopet-0.0.1-SNAPSHOT.jar
```

Biến mới: **`SOCKET_PORT`**, **`RESEND_API_KEY`**, **`MAIL_FROM`**. Ba biến SMTP cũ
(`MAIL_HOST`/`MAIL_PORT`/`MAIL_USER`/`MAIL_PASS`) không còn được đọc — mail đi qua HTTP API của
[Resend](https://resend.com) vì Render chặn cổng SMTP ra ngoài.

Chạy ở chế độ dev:

```bash
mvn spring-boot:run       # đúng cú pháp: có dấu hai chấm, KHÔNG phải `mvn springboot-run`
```

### Lỗi hay gặp

| Triệu chứng | Nguyên nhân | Cách sửa |
|---|---|---|
| `Unknown lifecycle phase "springboot-run"` | sai tên goal | dùng `mvn spring-boot:run` (gạch nối + dấu hai chấm) |
| `Web server failed to start. Port 8080 was already in use` | còn tiến trình cũ giữ cổng | đổi `APP_PORT`, hoặc tắt tiến trình đang chiếm cổng |
| `Không mở được Socket.IO trên cổng 8081` | cổng realtime bị chiếm | đổi `SOCKET_PORT`, hoặc `SOCKET_PORT=0` để tắt realtime |
| `Communications link failure` lúc khởi động | không có MySQL ở `DATABASE_HOSTNAME:DATABASE_PORT` | mặc định là `localhost:3306`; MySQL của docker-compose nằm ở cổng **3307** |
| `Referencing column ... are incompatible` | database đang trỏ tới có schema lẫn lộn từ lần chạy sai cấu hình cũ | xem `docs/MIGRATION_FINAL_REPORT.md` §6 |

### 3. Test

```bash
mvn test          # cần MySQL ở cổng 3307; hai bộ integration tự tạo database riêng
```

## Profile cấu hình

| Profile | ddl-auto | Dùng khi |
|---|---|---|
| `dev` (mặc định) | `update` | máy cá nhân — giống `synchronize: true` của TypeORM |
| `test` | `create-drop` | `mvn test`, database riêng, không đụng dev |
| `prod` | `validate` | chỉ kiểm tra entity khớp bảng thật, không tự sửa schema |

Không hard-code bí mật ở bất kỳ đâu; tất cả đọc từ biến môi trường.

## Cấu trúc

```
com.nguyenvu.lopet
├── config/        Cors, Jackson (định dạng ngày kiểu Node), Cloudinary, Web
├── bootstrap/     AuthorizationSeeder, AdminInitializer, StartupRunner
├── common/        response envelope, exception + handler tập trung, media
├── security/      JWT, @Auth, @RequirePermission, PermissionCatalog, OwnershipGuard
├── realtime/      Socket.IO server + gateway phát sự kiện
└── <module>/      account, auth, role, email, profile, group, post, comment,
                   friendship, message, notification, report, advertisement, advertiser
                   (mỗi module: controller / service / repository / entity / dto)
```

Bốn tầng phân quyền độc lập, đúng như bản Express:

1. **Permission** — `@RequirePermission` (hành động này có được phép không)
2. **Ownership** — `OwnershipGuard` (đúng tài nguyên của bạn không; `bypassRoles` khác nhau theo route)
3. **Relationship** — chỉ bạn bè mới xem được danh sách bạn bè
4. **Capability** — chỉ hồ sơ nhà quảng cáo `APPROVED` mới tạo được quảng cáo

## Khác biệt duy nhất với bản cũ

Express gắn Socket.IO vào chính HTTP server nên dùng chung cổng 8080. Ở Java, Tomcat sở hữu cổng
HTTP nên Socket.IO phải nghe cổng riêng (`SOCKET_PORT`, mặc định 8081). Chạy trực tiếp bằng
`java -jar`, client sửa một dòng:

```js
// trước
const socket = io("http://localhost:8080", { auth: { token } })
// sau
const socket = io("http://localhost:8081", { auth: { token } })
```

**Chạy bằng Docker thì khác biệt này biến mất**: image có sẵn nginx gộp hai cổng về một origin,
`/socket.io/` và REST API dùng chung URL đúng như bản Express — xem [Deploy](#deploy-render).

## Deploy (Render)

Render chỉ mở **một** cổng công khai cho mỗi service, lấy từ biến `$PORT` do nó tự cấp lúc chạy.
JVM này lại mở hai — Tomcat và netty-socketio — nên nếu để trần, Render chỉ định tuyến tới một
trong hai và `wss://.../socket.io/` không bao giờ bắt tay được. Image giải quyết bằng một lớp
nginx đứng trước:

```
Render ──$PORT──▶ nginx ──┬── /socket.io/ ──▶ 127.0.0.1:8081  netty-socketio
                          └── /           ──▶ 127.0.0.1:8080  Tomcat (REST)
```

| File | Vai trò |
|---|---|
| [`docker/nginx.conf.template`](docker/nginx.conf.template) | cấu hình proxy; `${PORT}` và hai cổng upstream được `envsubst` bơm vào lúc container khởi động |
| [`docker/entrypoint.sh`](docker/entrypoint.sh) | sinh cấu hình → **chờ 8080/8081 mở** → bật nginx → `exec java` (JVM giữ PID 1 để nhận SIGTERM) |

Chi tiết dễ bỏ sót: nginx chỉ mở cổng công khai **sau khi** Tomcat và Socket.IO đã nghe. Spring
mất ~30s để lên; bật nginx ngay từ giây đầu thì `$PORT` mở trong khi phía sau chưa có ai, Render
tưởng deploy xong và cho traffic vào, health check đập trúng 502 và deploy bị đánh trượt. Chờ
xong mới mở giữ đúng ngữ nghĩa của bản chưa có proxy: **cổng mở nghĩa là app sẵn sàng**.

Kèm theo: 502 do nginx sinh ra không có header CORS, nên trình duyệt hiển thị nó thành
`No 'Access-Control-Allow-Origin' header is present` — một thông báo trỏ sai hoàn toàn hướng.
Gặp lỗi CORS lạ thì kiểm tra bằng `curl -i` trước, xem mã trả về thật là gì.

Frontend dùng đúng một origin cho cả hai:

```js
const socket = io("https://<service>.onrender.com", { auth: { token } })
```

### ⚠️ Biến môi trường cần **xoá** khỏi Render Environment

`APP_PORT`, `SOCKET_PORT`, `SOCKET_HOSTNAME` — nếu còn sót lại từ lần deploy trước thì xoá đi.
Hai cổng nội bộ 8080/8081 giờ cố định trong `docker/entrypoint.sh` và truyền bằng tham số dòng
lệnh (`--server.port`, `--lopet.socket.port`), thứ có precedence cao nhất trong Spring — các biến
đó có tồn tại cũng không còn tác dụng, chỉ gây hiểu nhầm là còn chỉnh được. Cổng duy nhất còn ý
nghĩa là `$PORT`, và **Render tự cấp**, đừng tự khai.

### Build và test tại máy

```bash
docker build -t lopet-be .
docker run --rm -p 3000:3000 -e PORT=3000 --env-file .env lopet-be
```

`PORT` phải trùng cổng bên trong của `-p`. Đặt `PORT=8080` hay `8081` vẫn chạy được: entrypoint
thấy trùng cổng nội bộ thì dời upstream sang 18080/18081, vì thứ bắt buộc phải nghe đúng `$PORT`
chỉ có nginx. Kiểm tra cả hai đường:

```bash
curl -i http://localhost:3000/api/v1/roles                  # REST qua nginx
curl -i "http://localhost:3000/socket.io/?EIO=4&transport=polling"   # handshake Socket.IO
```

Handshake trả `0{"sid":"...","upgrades":["websocket"],...}` là nginx đã định tuyến đúng sang
netty-socketio.

Các khác biệt còn lại (thông điệp lỗi handshake socket, số phần tử trong mảng `errors` của
validation, ranh giới transaction) đều được liệt kê ở
[`docs/MIGRATION_FINAL_REPORT.md`](docs/MIGRATION_FINAL_REPORT.md) §4.

## Tài liệu

| File | Nội dung |
|---|---|
| [`docs/MIGRATION_FINAL_REPORT.md`](docs/MIGRATION_FINAL_REPORT.md) | Đối chiếu cuối: số liệu, bằng chứng, mọi khác biệt được khai báo |
| [`docs/API_MIGRATION_MATRIX.md`](docs/API_MIGRATION_MATRIX.md) | 72 đường dẫn: auth, authz, body, response, mã lỗi |
| [`docs/MIGRATION_FEATURE_MATRIX.md`](docs/MIGRATION_FEATURE_MATRIX.md) | 33 module/feature kèm business rule chi tiết |
| [`docs/DATABASE_MIGRATION_NOTES.md`](docs/DATABASE_MIGRATION_NOTES.md) | 18 bảng, chỗ không map 1:1 được, kết quả so schema |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Kiến trúc, ánh xạ middleware → Spring, transaction, hiệu năng |
