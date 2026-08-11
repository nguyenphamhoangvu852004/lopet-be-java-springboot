# ARCHITECTURE — lopet-be-java-springboot

Mục tiêu: thay thế `lopet-be` mà **client hiện tại không phải sửa một dòng nào**. Kiến trúc bên
trong được phép khác; API contract và business behavior thì không.

## 1. Ngăn xếp

| Thành phần | TS | Java |
|---|---|---|
| Runtime | Node 20 | Java 21 |
| Framework | Express 5 | Spring Boot 4.1 (`spring-boot-starter-webmvc`) |
| ORM | TypeORM 0.3 | Spring Data JPA + Hibernate 7 |
| DB | MySQL 8 | MySQL 8 (`mysql-connector-j`) — **cùng schema** |
| Cache/OTP | `redis` client | Spring Data Redis (Lettuce) |
| Auth | `jsonwebtoken` | `jjwt` + Spring Security filter |
| Hash | bcryptjs (cost 10) | `BCryptPasswordEncoder(10)` — **cùng định dạng `$2a$`** |
| Validation | Joi | Bean Validation (Jakarta) + validator tuỳ biến |
| Realtime | socket.io 4.8 | **netty-socketio** (cùng protocol EIO4 → client socket.io-client không đổi) |
| Upload | multer + Cloudinary SDK | `MultipartFile` + Cloudinary Java SDK |
| Mail | nodemailer (SMTP) | **Resend HTTP API** (`com.resend:resend-java`) — SMTP bị hosting chặn cổng |
| Log | winston + daily-rotate | Logback + `RollingFileAppender` |
| Test | Jest + supertest | JUnit 5 + MockMvc + Testcontainers/MySQL thật |

## 2. Cấu trúc package

```
com.nguyenvu.lopet
├── LopetApplication.java
├── config/            CorsConfig, JacksonConfig, RedisConfig, MailConfig, CloudinaryConfig, SocketIoConfig
├── bootstrap/         AuthorizationSeeder, AdminInitializer   (thay cho seedAuthorization + InitAdmin)
├── common/
│   ├── response/      ApiResponse<T>, ResponseFactory
│   ├── exception/     HttpException + NotFound/BadRequest/Conflict/Unauthorized/Forbidden,
│   │                  GlobalExceptionHandler
│   ├── media/         CloudinaryService
│   └── util/          OtpGenerator, DateTimes
├── security/
│   ├── jwt/           JwtService, JwtAuthenticationFilter, UserPrincipal
│   ├── authz/         PermissionCatalog, RequirePermission(+Aspect), OwnershipGuard,
│   │                  FriendOrSelfGuard, ApprovedAdvertiserGuard
│   └── SecurityConfig.java
├── realtime/          SocketIoConfig (server + xác thực), SocketIoServerRunner, RealtimeGateway
├── account/  auth/  role/  email/  profile/
├── group/    post/ (media, like)  comment/
├── friendship/  message/  notification/
├── report/   advertisement/  advertiser/
└── (mỗi module: controller / service / repository / entity / dto / mapper)
```

Mỗi module theo lát cắt dọc: `controller → service → repository → entity`, DTO riêng cho request và
response, mapper thủ công hoặc MapStruct. Controller **không** chứa business logic và **không** gọi
repository.

## 3. Ánh xạ 4 tầng phân quyền của Express sang Spring

| TS | Java | Ghi chú |
|---|---|---|
| `verifyToken()` | `JwtAuthenticationFilter` (bắt buộc) | thiếu token → **400** như TS, không phải 401 |
| `optionalAuth()` | cùng filter, chế độ mềm | không token → anonymous; token hỏng → **401** |
| `requirePermission(...)` | `@RequirePermission({"a","b"})` + AOP | ngữ nghĩa **OR**, wildcard `*` và `resource:*` |
| `requireOwnership({load, ownerOf, bypassRoles})` | `OwnershipGuard.check(...)` gọi ở đầu service, hoặc `@PreAuthorize` cho ca đơn giản | phải giữ đúng thứ tự: bypassRole xét **trước** khi load |
| `requireFriendOrSelf(...)` | `FriendOrSelfGuard` | không role nào bypass |
| `requireApprovedAdvertiser(...)` | `ApprovedAdvertiserGuard` | chạy **trước** khi upload ảnh |

**Vị trí gọi guard**: Express đặt middleware trước handler, kể cả trước `multer`. Trong Spring,
`MultipartFile` đã được parse xong khi vào controller. Để giữ đúng tính chất "chặn trước khi tốn một
lượt upload lên Cloudinary", guard được gọi **ở đầu service/controller, trước lệnh upload** — điểm
tốn kém thật sự là Cloudinary chứ không phải việc parse multipart.

`SecurityConfig` để `SessionCreationPolicy.STATELESS`, tắt CSRF, tắt form login/basic, và **không**
dùng `hasRole()` — mọi quyết định đi qua PermissionCatalog để trùng nguồn sự thật với TS.

## 4. Contract phải giữ nguyên

1. **Envelope thành công**: `{statusCode, message, data}` — `statusCode` lặp lại trong body.
2. **Envelope lỗi**: `{statusCode, message}` — **không** có `data`, không có `timestamp`, không có `path`.
3. **Envelope lỗi validation**: `{statusCode:400, message:"Validation error", errors:[{field, message}]}`.
4. Message lỗi tiếng Việt giữ **nguyên văn** (client đang hiển thị trực tiếp).
5. Mã lỗi lệch chuẩn giữ nguyên: thiếu token → 400; không đủ quyền xem bài → 404; login sai mật khẩu → 400.
6. Tên field trong DTO giữ nguyên, kể cả chỗ đặt tên lạ: `notificationId`, `linkReferfence` (sai
   chính tả trong DTO ads), `postId` thay vì `id`, `likeList`/`listLike` khác nhau giữa hai DTO.
7. Tên sự kiện socket giữ nguyên, kể cả `chat messsage` (ba chữ `s`).
8. Hai tiền tố `/v1/auth` và `/v1/password` cùng trỏ tới bộ controller auth.

## 5. Transaction

`@Transactional` chỉ đặt ở service, đúng các thao tác ghi nhiều bảng:

| Luồng | Phạm vi |
|---|---|
| `AuthService.register` | tạo account (1 bảng) — vẫn cần vì có tương tác Redis trước đó |
| `AccountService.setRolesToAccount` | xoá toàn bộ `account_role` + ghi lại danh sách mới |
| `GroupService.createGroup` | tạo `groups` + `group_members(OWNER)` |
| `PostService.create` | tạo `posts` + n `post_medias` |
| `PostService.update` | sửa post + xoá media không giữ + tạo media mới |
| `ReportService.update` | cập nhật nhiều bản ghi report cùng target |
| `AdvertiserService.review` | cập nhật status + cột audit |

Các thao tác đọc dùng `@Transactional(readOnly = true)` ở tầng service. **Không** đặt
`@Transactional` mặc định trên toàn bộ class controller/repository.

> Lưu ý parity: TS **không** bọc transaction ở bất kỳ luồng nào (mỗi lệnh `save()` là một
> transaction riêng). Việc thêm transaction trong Java là cải thiện tính nhất quán và **không** đổi
> kết quả của luồng thành công; ở luồng lỗi, Java rollback trong khi TS để lại dữ liệu dở dang.
> Đây là khác biệt duy nhất được cố ý chấp nhận, ghi lại ở MIGRATION_FINAL_REPORT.

## 6. Hiệu năng

* Mọi truy vấn danh sách bài dùng **một** query có `JOIN FETCH` cho `accounts`, `postMedias`,
  `group`, `postLikes.account` — đúng như `readQuery()` của TS.
* Có **hai** collection bag cùng lúc (`postMedias` + `postLikes`) → `MultipleBagFetchException`.
  Xử lý: khai `Set` cho các collection được fetch join, hoặc tách thành 2 truy vấn với
  `@BatchSize`. Không được đổi kết quả trả về.
* Phân trang: TS dùng `take(10)` cho suggest (2 truy vấn) — Java dùng `Pageable` tương đương để
  không cắt nhầm hàng khi join 1-n.
* N+1 hiện có trong TS (`CommentService` gọi profile theo từng comment) được gộp trong Java, miễn là
  response giống hệt.
* `LazyInitializationException`: mọi mapping entity→DTO nằm **trong** service (còn transaction).

## 7. Cấu hình

`application.yml` (chung) + `application-dev.yml` / `application-test.yml` / `application-prod.yml`.
Toàn bộ giá trị nhạy cảm lấy từ biến môi trường, trùng tên với `example.env` của TS:
`DATABASE_*`, `REDIS_*`, `APP_PORT`, `DOMAIN_CORS`, `ACCESS_TOKEN_SECRET`, `REFRESH_TOKEN_SECRET`,
`ACCESS_TOKEN_EXPIRES_IN`, `REFRESH_TOKEN_EXPIRES_IN`, `MAIL_*`, `CLOUDINARY_*`, `INIT_ADMIN_*`,
`LOG_LEVEL`. Không hard-code bí mật ở bất kỳ đâu.

## 8. Thứ tự khởi động (khớp `bootstrap()` của TS)

1. Kết nối DB → 2. Kết nối Redis → 3. Verify SMTP (lỗi chỉ log, **không** chặn khởi động) →
4. `AuthorizationSeeder` (permissions → roles → role_permission, idempotent) →
5. `AdminInitializer` (tạo tài khoản từ `INIT_ADMIN_*` nếu chưa có + gán role ADMIN) →
6. Mở HTTP + Socket.IO.
