# lopet-be-java-springboot

Bản migrate Java/Spring Boot của [`lopet-be`](../lopet-be) (TypeScript + ExpressJS + TypeORM).

Mục tiêu: **thay thế trực tiếp** backend cũ — dùng lại đúng database và đúng file `.env`. Ngoại lệ
duy nhất là tầng realtime: nó đã chuyển sang STOMP over WebSocket, nên frontend phải viết lại phần
đó (xem [Khác biệt duy nhất](#khác-biệt-duy-nhất-với-bản-cũ)).

`lopet-be` là **source of truth**. Ở đâu hành vi của nó khác với best practice hay khác với cách một
mạng xã hội "nên" hoạt động, bản Java giữ theo `lopet-be`.

---

## Tình trạng

| Hạng mục | Kết quả |
|---|---|
| Endpoint | 72/72 đường dẫn, 0 thiếu |
| Destination realtime | 8/8 |
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
| Realtime | socket.io 4 | STOMP over WebSocket (`spring-boot-starter-websocket`) |
| Upload | multer + Cloudinary | `MultipartFile` + Cloudinary SDK |
| Test | Jest + supertest | JUnit 5 + Mockito + MySQL thật |

## Chạy dự án

Hai môi trường, mỗi môi trường một file compose và một file `.env`. Khác biệt nằm hoàn toàn ở cấu
hình — cùng một image được kiểm ở dev rồi đẩy lên prod.

| | Dev | Production |
|---|---|---|
| Compose | `docker-compose-dev.yml` | `docker-compose.yml` |
| Env | `.env.dev` | `.env.prod` |
| Spring profile | `dev` | `prod` |
| MySQL / Redis | container, publish 3307 / 6379 ra host | container, **không** publish cổng nào |
| Vào từ ngoài | `localhost:8080` (REST + WebSocket `/ws`) | `https://api.nguyenvu.io.vn` qua nginx |
| `ddl-auto` | `update` | `${JPA_DDL_AUTO:update}` — xem cảnh báo trong `application-prod.yml` |
| Dữ liệu demo | có (`SEED_DEMO=true`) | không, cứng |
| Log | `debug` cho package app | `warn` root / `info` app |
| CORS | `*` | danh sách origin cụ thể |
| Actuator | mở rộng, health chi tiết | `health,info,metrics`, nginx chặn `/actuator/` từ ngoài |

### Dev

```bash
cp .env.example .env.dev          # rồi điền giá trị
docker compose --env-file .env.dev -f docker-compose-dev.yml up -d --build
```

`--env-file` là **bắt buộc**, không phải tuỳ chọn. `env_file:` trong compose chỉ bơm biến vào *bên
trong* container, còn `${VAR}` viết ở thân file compose (mật khẩu MySQL) được thay lúc compose đọc
file — hai cơ chế khác nhau. Thiếu nó, compose dừng ngay với thông báo tên biến còn thiếu.

```bash
docker compose --env-file .env.dev -f docker-compose-dev.yml logs -f lopet-backend
docker compose --env-file .env.dev -f docker-compose-dev.yml down          # giữ dữ liệu
docker compose --env-file .env.dev -f docker-compose-dev.yml down -v       # xoá luôn volume
```

Kiểm tra nhanh:

```bash
curl -s localhost:8080/actuator/health
curl -i -N -H "Connection: Upgrade" -H "Upgrade: websocket"      -H "Sec-WebSocket-Version: 13" -H "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ=="      http://localhost:8080/ws                       # phải trả 101 Switching Protocols
```

### Chạy backend ngoài Docker

Vẫn dùng được khi cần debug từ IDE — chỉ dựng hạ tầng rồi trỏ app vào cổng đã publish:

```bash
docker compose --env-file .env.dev -f docker-compose-dev.yml up -d lopet-mysql lopet-redis
DATABASE_HOSTNAME=127.0.0.1 DATABASE_PORT=3307 REDIS_HOSTNAME=127.0.0.1 ./mvnw spring-boot:run
```

Hoặc copy `.env.dev` thành `.env` (đổi hai host thành `127.0.0.1`, `DATABASE_PORT=3307`) —
`application.yml` khai `spring.config.import: optional:file:.env[.properties]` nên nó được nạp tự
động.

### Production

Lần đầu trên VPS, sau khi DNS của `API_DOMAIN` đã trỏ về máy:

```bash
./scripts/init-letsencrypt.sh --staging    # thử trước, không tính vào rate limit
./scripts/init-letsencrypt.sh              # chứng chỉ thật
docker compose --env-file .env.prod -f docker-compose.yml up -d --build
```

`init-letsencrypt.sh` tồn tại để gỡ thế kẹt: nginx từ chối khởi động khi `ssl_certificate` trỏ vào
file chưa có, còn certbot lại cần nginx đang chạy để phục vụ ACME challenge. Script dựng một chứng
chỉ tự ký giả cho nginx lên được, lấy chứng chỉ thật đè lên, rồi reload. Sau lần đó service
`lopet-certbot` tự gia hạn mỗi 12h và `lopet-nginx` tự reload mỗi 6h để nạp cert mới.

Deploy phiên bản mới:

```bash
git pull
docker compose --env-file .env.prod -f docker-compose.yml up -d --build lopet-backend
docker compose --env-file .env.prod -f docker-compose.yml exec lopet-nginx nginx -s reload
```

Dòng `nginx -s reload` là **bắt buộc** sau khi dựng lại backend: nginx phân giải hostname của
upstream một lần lúc nạp cấu hình rồi nhớ địa chỉ IP đó. Container backend mới có IP mới, nên
không reload thì mọi request đi vào một IP đã chết cho tới lần reload định kỳ 6h sau.

### Lỗi hay gặp

| Triệu chứng | Nguyên nhân | Cách sửa |
|---|---|---|
| `required variable DATABASE_PASSWORD is missing a value` | quên `--env-file` | thêm `--env-file .env.dev` (hoặc `.env.prod`) |
| `env file .env.prod not found` | chưa tạo file env cho môi trường đó | `cp .env.example .env.prod` rồi điền |
| `Bind for 0.0.0.0:6379 failed: port is already allocated` | còn container cũ giữ cổng | `docker ps` rồi dừng nó, hoặc đổi `HOST_REDIS_PORT` trong `.env.dev` |
| `Communications link failure` lúc khởi động | app lên trước khi MySQL nhận kết nối | đã xử lý bằng `depends_on: condition: service_healthy`; nếu chạy app ngoài Docker thì đợi `docker compose ps` báo mysql `healthy` |
| 413 khi upload ảnh/video | `client_max_body_size` của nginx nhỏ hơn `MAX_REQUEST_SIZE` | hai giá trị phải đi cùng nhau — xem `nginx/conf.d/lopet.conf` |
| WebSocket không bắt tay được qua nginx | `location /ws` nằm sau `location /`, mà khối đó đặt `Connection ""` cho keepalive | giữ `location /ws` **trước** `location /` như trong `nginx/conf.d/lopet.conf` |
| `Referencing column ... are incompatible` | database đang trỏ tới có schema lẫn lộn từ lần chạy sai cấu hình cũ | xem `docs/MIGRATION_FINAL_REPORT.md` §6 |

### Test

```bash
docker compose --env-file .env.dev -f docker-compose-dev.yml up -d lopet-mysql
./mvnw test          # cần MySQL ở cổng 3307; các bộ integration tự tạo database riêng
```

## Profile cấu hình

| Profile | File | ddl-auto | Dùng khi |
|---|---|---|---|
| `dev` (mặc định) | `application-dev.yml` | `update` | máy cá nhân — giống `synchronize: true` của TypeORM |
| `test` | `src/test/resources/application-test.yml` | `update`, database riêng mỗi test class | `./mvnw test` |
| `prod` | `application-prod.yml` | `${JPA_DDL_AUTO:update}` | VPS |

`application.yml` giữ phần dùng chung và mọi biến môi trường; hai file profile chỉ chứa thứ *khác
nhau* giữa hai môi trường. Không hard-code bí mật ở bất kỳ đâu.

## Cấu trúc

```
com.nguyenvu.lopet
├── config/        Cors, Jackson (định dạng ngày kiểu Node), Cloudinary, Web
├── bootstrap/     AuthorizationSeeder, AdminInitializer, StartupRunner
├── common/        response envelope, exception + handler tập trung, media
├── security/      JWT, @Auth, @RequirePermission, PermissionCatalog, OwnershipGuard
├── realtime/      WebSocket/STOMP: endpoint /ws, xác thực frame CONNECT, gateway phát sự kiện
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

Tầng realtime chuyển từ Socket.IO sang **STOMP over WebSocket**, endpoint `/ws` trên chính cổng REST.
Đây là khác biệt duy nhất buộc frontend phải sửa code, và nó là breaking: `socket.io-client` không
nói được STOMP nên không có đường giữ nguyên client cũ.

```js
// trước
const socket = io("http://localhost:8081", { auth: { token } })
socket.on("chat messsage", handler)

// sau
const client = new Client({
  brokerURL: "ws://localhost:8080/ws",
  connectHeaders: { Authorization: `Bearer ${token}` },
})
client.onConnect = () => client.subscribe(`/topic/user.${myId}/chat`, handler)
client.activate()
```

Bù lại, cổng 8081 biến mất hoàn toàn: dev lẫn prod đều chỉ còn một cổng, và `/ws` dùng chung origin
với REST đúng như bản Express từng làm với `/socket.io/`.

Bảng ánh xạ đầy đủ 7 sự kiện cũ → destination mới, thông điệp lỗi và snippet client:
[`docs/REALTIME_WEBSOCKET_MIGRATION.md`](docs/REALTIME_WEBSOCKET_MIGRATION.md).

## Deploy

Kiến trúc production: chỉ nginx publish cổng, ba service còn lại sống trong network nội bộ.

```
Internet ──80/443──▶ lopet-nginx ──┬── /ws ──▶ lopet-backend:8080  STOMP over WebSocket
                                   └── /   ──▶ lopet-backend:8080  Tomcat (REST)

                     lopet-backend ──┬──▶ lopet-mysql:3306
                                     └──▶ lopet-redis:6379   (requirepass)
```

| File | Vai trò |
|---|---|
| [`docker-compose.yml`](docker-compose.yml) | 5 service: mysql, redis, backend, nginx, certbot |
| [`nginx/conf.d/lopet.conf`](nginx/conf.d/lopet.conf) | reverse proxy, TLS, `client_max_body_size`, nâng cấp WebSocket |
| [`scripts/init-letsencrypt.sh`](scripts/init-letsencrypt.sh) | xin chứng chỉ TLS lần đầu |
| [`Dockerfile`](Dockerfile) | image dùng chung cho cả hai môi trường |

Vài điểm được xử lý sẵn, dễ bỏ sót nếu dựng lại từ đầu:

- **`depends_on: condition: service_healthy`** — MySQL cần thêm ~20s sau khi container được tạo mới
  nhận kết nối. `depends_on` trần chỉ đợi container tồn tại, nên backend lên trước và chết với
  `Communications link failure`.
- **`client_max_body_size 500m`** — mặc định của nginx là 1MB; thiếu dòng này thì mọi upload chết ở
  tầng proxy với 413 dù `MAX_REQUEST_SIZE` của app đã là 500MB.
- **`location /ws` đứng trước `location /`** — khối `location /` đặt `Connection ""` để keepalive
  tới upstream hoạt động; một handshake rơi vào đó thì không bao giờ nâng cấp lên WebSocket được.
- **`server.forward-headers-strategy: framework`** — thiếu nó, mọi URL Spring tự sinh mang scheme
  `http` và cổng nội bộ 8080 thay vì domain thật.
- **Vòng gia hạn của certbot + reload của nginx** — certbot gia hạn thành công nhưng nginx giữ cert
  cũ trong bộ nhớ cho tới lần restart tay, nên site vẫn chết đúng ngày cert hết hạn.

Frontend dùng đúng một origin cho cả REST lẫn realtime:

```js
const client = new Client({
  brokerURL: "wss://api.nguyenvu.io.vn/ws",
  connectHeaders: { Authorization: `Bearer ${token}` },
})
```

### Kiểm tra sau khi deploy

```bash
docker compose --env-file .env.prod -f docker-compose.yml ps      # mọi service phải healthy
curl -i https://api.nguyenvu.io.vn/api/v1/roles                   # REST qua nginx
curl -i https://api.nguyenvu.io.vn/actuator/health                # phải trả 404 (nginx chặn)
curl -i -N -H "Connection: Upgrade" -H "Upgrade: websocket"      -H "Sec-WebSocket-Version: 13" -H "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ=="      https://api.nguyenvu.io.vn/ws
```

Bắt tay trả `101 Switching Protocols` là nginx đã định tuyến và nâng cấp đúng.

Các khác biệt còn lại (thông điệp lỗi bắt tay realtime, số phần tử trong mảng `errors` của
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
| [`docs/REALTIME_WEBSOCKET_MIGRATION.md`](docs/REALTIME_WEBSOCKET_MIGRATION.md) | **Cho frontend**: Socket.IO → STOMP, bảng ánh xạ destination, snippet client |
