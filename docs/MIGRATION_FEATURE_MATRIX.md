# MIGRATION_FEATURE_MATRIX

Kiểm kê 100% module/feature tìm thấy trong `lopet-be`. `lopet-be` là **source of truth**;
cột "Java Implementation" ghi nơi hành vi đó phải được tái hiện.

Trạng thái: `TODO` | `WIP` | `DONE` (chỉ đánh `DONE` sau khi đã verify hành vi, không phải sau khi compile).

---

## A. Bảng tổng quan module

| # | Feature | TS/Express Implementation | Java Implementation | Status |
|---|---|---|---|---|
| 1 | Bootstrap & vòng đời khởi động | `src/index.ts` | `LopetApplication` + `ApplicationRunner` (seed) | DONE |
| 2 | Authentication (login/signup/verify/reset) | `modules/auth/**` | `auth/**` | DONE |
| 3 | OTP qua email + Redis | `modules/email/**`, `config/emailConfig.ts` | `email/**`, `config/MailConfig` | DONE |
| 4 | Authorization — permission catalog | `authz/permission.catalog.ts`, `middlewares/requirePermission.ts` | `security/authz/PermissionCatalog` + `@RequirePermission` | DONE |
| 5 | Authorization — ownership | `middlewares/requireOwnership.ts` | `security/authz/OwnershipGuard` | DONE |
| 6 | Authorization — quan hệ bạn bè | `middlewares/requireFriendOrSelf.ts` | `security/authz/FriendOrSelfGuard` | DONE |
| 7 | Authorization — capability nhà quảng cáo | `middlewares/requireApprovedAdvertiser.ts` | `security/authz/ApprovedAdvertiserGuard` | DONE |
| 8 | JWT sinh/kiểm token | `utils/jwt.util.ts`, `middlewares/verifyToken.ts`, `middlewares/optionalAuth.ts` | `security/jwt/**` + `JwtAuthenticationFilter` | DONE |
| 9 | Seed permission/role | `authz/permission.seed.ts`, `modules/role/init.role.ts` | `bootstrap/AuthorizationSeeder` | DONE |
| 10 | Seed tài khoản admin | `modules/admin/**` | `bootstrap/AdminInitializer` | DONE |
| 11 | Account (CRUD/ban/setRoles/suggest) | `modules/account/**` | `account/**` | DONE |
| 12 | Role (đọc danh sách) | `modules/role/**` | `role/**` | DONE |
| 13 | Profile | `modules/profile/**` | `profile/**` | DONE |
| 14 | Group + group_members | `modules/group/**` | `group/**` | DONE |
| 15 | Post + scope/visibility | `modules/post/**` | `post/**` | DONE |
| 16 | Post media | `modules/postMedia/**` | `post/media/**` | DONE |
| 17 | Post like | `modules/postLike/**` | `post/like/**` | DONE |
| 18 | Comment (có cây trả lời 1 cấp tham chiếu parent) | `modules/comment/**` | `comment/**` | DONE |
| 19 | FriendShip | `modules/friendShip/**` | `friendship/**` | DONE |
| 20 | Message (chat 1-1) | `modules/message/**` | `message/**` | DONE |
| 21 | Notification | `modules/notification/**` | `notification/**` | DONE |
| 22 | Realtime Socket.IO | `middlewares/socketio.ts`, `res.io` | `realtime/**` (netty-socketio) | DONE |
| 23 | Report | `modules/report/**` | `report/**` | DONE |
| 24 | Advertisement | `modules/advertisement/**` | `advertisement/**` | DONE |
| 25 | Advertiser profile | `modules/advertiser/**` | `advertiser/**` | DONE |
| 26 | Upload ảnh/video Cloudinary | `config/cloudinary.ts`, `config/multerConfig.ts` | `common/media/CloudinaryService` | DONE |
| 27 | Validation | `validation/schema.validation.ts`, `middlewares/validation.middleware.ts` | Bean Validation + `ValidationExceptionHandler` | DONE |
| 28 | Error handling tập trung | `error/error.custom.ts`, `middlewares/globalExceptionHandler.middleware.ts` | `common/exception/**` + `@RestControllerAdvice` | DONE |
| 29 | Response envelope | `response/api.response.ts` | `common/response/ApiResponse` | DONE |
| 30 | CORS | `middlewares/cors.middleware.ts` | `config/CorsConfig` | DONE |
| 31 | Logging | `config/logger.ts` (winston + daily rotate) | Logback + rolling file | DONE |
| 32 | Cấu hình môi trường | `config/env.ts` (dotenv) | `application*.yml` + env var | DONE |
| 33 | Soft delete | `entities/base.entity.ts` (`@DeleteDateColumn`) | `@SQLDelete`/`@SQLRestriction` — xem DATABASE_MIGRATION_NOTES | DONE |

**Không tồn tại trong `lopet-be`** (không được tự thêm): refresh-token endpoint, logout, đổi mật
khẩu khi đã đăng nhập, follow/unfollow, block/unblock (enum `BLOCKED` có nhưng **không endpoint nào
dùng**), chia sẻ bài, hashtag, mention, phân trang (không endpoint nào có page/size), cron job,
background job, cache tầng đọc (Redis chỉ dùng cho OTP).

---

## B. Chi tiết từng feature

### 2. Authentication

* **Endpoint**: xem API_MIGRATION_MATRIX §1 (4 endpoint × 2 tiền tố).
* **Auth**: không cần token.
* **Validation (Joi)**:
  * register: `email` email + min 12 + trim + strict; `username` required trim strict;
    `password`/`confirmPassword` min 6 trim strict.
  * login: `username` required; `password` min 6.
  * reset: `email` email; `password`/`confirmPassword` min 6.
  * `strict()` = **không tự ép kiểu** → gửi số cho trường string là 400.
  * `abortEarly:false` → trả về **mọi** lỗi cùng lúc.
* **Business rules**:
  1. Mật khẩu băm bcrypt cost **10** (`bcryptConfig.saltRounds`).
  2. Đăng ký bắt buộc có cờ `email_verified:<email>` trong Redis; cờ bị **`del` ngay** trước khi
     kiểm tra trùng.
  3. Tài khoản `isBanned=1` không đăng nhập được (400, không phải 403).
  4. Reset password tiêu thụ cờ bằng `getDel` (nguyên tử) trước khi ghi.
  5. Roles nằm trong JWT, không nằm trong response login.
* **Bảng**: `accounts`, `account_role`, `roles`.
* **Side effect**: xoá key Redis.
* **Realtime**: không.

### 4–7. Authorization (bốn tầng độc lập)

| Tầng | Câu hỏi | Nguồn dữ liệu | Bypass |
|---|---|---|---|
| Permission | "Hành động này có được phép?" | `resolvePermissions(roles)` từ JWT, thuần code | `*` của ADMIN |
| Ownership | "Đúng tài nguyên của bạn?" | load resource từ DB | mặc định `[ADMIN]`, có route đặt `[]` |
| Relationship | "Bạn có quan hệ gì với chủ?" | bảng `friend_ships` | không ai |
| Capability | "Có tư cách chạy quảng cáo?" | `advertiser_profiles.status` | không ai |

**Baseline permission** (mọi tài khoản đã đăng nhập, **không cần bản ghi role**):
`post:create`, `post:update:own`, `post:delete:own`, `comment:create`, `comment:delete:own`,
`group:create`, `group:update:own`, `group:delete:own`, `report:create`, `profile:update:own`,
`friendship:manage:own`, `advertiser:register`, `ads:create`, `ads:update:own`, `ads:delete:own`.

**Staff permission**: `account:read`, `account:ban`, `account:delete`, `account:setRole`,
`report:read`, `report:resolve`, `post:delete`, `group:delete`, `ads:review`, `advertiser:read`,
`advertiser:approve`.

**Role → permission**:
* `ADMIN` → `*` (wildcard, qua tất)
* `MODERATOR` → `report:read`, `report:resolve`, `post:delete`, `group:delete`, `account:ban`, `ads:review`, `advertiser:read`
* `SUPPORT` → `account:read`, `report:read`, `advertiser:read`

`requirePermission(a, b)` dùng **OR** (`required.some(...)`). `hasPermission` chấp nhận `*` và
`<resource>:*`.

**Ownership bypass đặc biệt (phải giữ nguyên):**

| Route | bypassRoles | Lý do |
|---|---|---|
| PUT `/posts/:postId` | `[]` | kiểm duyệt thì xoá, không viết hộ |
| DELETE `/posts/:id` | `[ADMIN]` | staff xoá bài vi phạm |
| DELETE `/comments/:commentId` | `[ADMIN]` | như trên |
| PATCH `/profiles/:id` | `[]` | hồ sơ là dữ liệu riêng |
| GET/PATCH `/messages/*` | `[]` | nội dung riêng tư |
| GET `/friendships/:id` | (không dùng requireOwnership) | ADMIN cũng không xem |
| PUT/DELETE `/advertisements/*` | `[ADMIN]` | |

**Ownership load qua bộ lọc quyền xem**: post và comment nạp bằng `getOneVisibleTo` để 403 và 404
không phân biệt được → chống dò id.

### 15. Post

* **Bảng**: `posts`, `post_medias`, `post_likes`, `groups`, `group_members`, `friend_ships`, `accounts`.
* **Quan hệ**: post→account (ManyToOne, CASCADE), post→group (ManyToOne nullable, CASCADE),
  post→post_medias (OneToMany cascade), post→post_likes (OneToMany cascade), post→comments (OneToMany cascade).
* **Business rules**: xem API §6. Thêm:
  * `postType` được suy ra bằng `setType()` (`group != null ? GROUP : USER`) — **cột nullable**,
    dữ liệu cũ có thể NULL nên **mọi truy vấn quyền dùng `group_id`**, không dùng `postType`.
  * `update`: không đổi được group; `oldIdsMedia` là danh sách media **giữ lại**, phần còn lại bị
    xoá bằng `DELETE ... WHERE post_id=? AND id NOT IN (...)`.
  * `like`/`unlike` idempotent, trả 200 kèm message khác nhau chứ không lỗi.
  * Sắp xếp: mọi list `createdAt DESC`. `getSuggestList` dùng `take(10)` (2 truy vấn) chứ không
    `limit(10)` — nếu không, join 1-n cắt nhầm hàng.
* **Side effect**: upload Cloudinary trước khi gọi service (kể cả khi service sau đó ném lỗi).
* **Realtime**: không có event nào cho post.

### 18. Comment

* Cây: `parent` ManyToOne self, `replies` OneToMany, `onDelete: CASCADE` — xoá comment cha xoá luôn con.
* Response `getCommentAllFromPost` **phẳng**, mỗi phần tử có `replyToCommentId` (undefined nếu gốc).
* Tạo reply: comment cha **phải thuộc đúng post** đang được kiểm quyền, nếu không → 400 `No comment found`.
* Xoá: service **không** kiểm ownership (đã kiểm ở middleware) — giữ nguyên để staff xoá được.
* N+1 hiện có: mỗi comment gọi `profileRepo.findByAccountId` một lần. Java được phép gộp truy vấn
  miễn là response **giống hệt**.

### 19. FriendShip

* Trạng thái: `PENDING | ACCEPTED | REJECTED | BLOCKED`. Không endpoint nào tạo `BLOCKED`.
* `create` chỉ chặn trùng **đúng chiều** (`sender=A, receiver=B`); chiều ngược lại vẫn tạo được →
  giữ nguyên hành vi.
* `areFriends(a,b)`: `a==b` trả **true**; ngược lại đếm bản ghi ACCEPTED hai chiều.
* `delete` dò hai chiều rồi `remove()` (xoá cứng).
* `sender`/`receiver` khai `eager: true`.

### 21. Notification + 22. Realtime

* `objectType` chỉ nhận `POST` | `MESSAGE`; giá trị khác → **cột không được gán** → lỗi DB
  (`objectType` NOT NULL).
* `updateStatus` chấp nhận `SENT|DELIVERED|READ`, khác → 400 `Invalid status: <x>`.
* `findById` dùng `withDeleted: true` (đọc được cả bản ghi đã soft-delete).
* Không endpoint nào kiểm người gọi có phải `receptor` — giữ nguyên (đây là hành vi hiện tại).
* Socket: xem API §15.

### 24–25. Quảng cáo

* Vòng đời tư cách: `PENDING → APPROVED → SUSPENDED`, chỉ `APPROVED` được tạo/sửa quảng cáo.
* `advertisements.advertiser_id` trỏ tới `advertiser_profiles`, **không** tới `accounts`.
* `update` cố tình **không** cho đổi `advertiser` (không chuyển chủ sở hữu).
* Enum `ADSTATUS {DRAFT, REVIEW, ACTIVE, REJECTED}` tồn tại và có default `DRAFT`, nhưng
  **không endpoint nào đọc/ghi** — chỉ cần giữ cột.
* Permission `ads:review` được seed nhưng chưa route nào dùng.

### 23. Report

* `targetType`: `USER | GROUP | POST`; `action`: `PENDING | CANCELLED | APPROVED`.
* `create` **không** kiểm target có tồn tại (dù service có inject `groupRepo`/`postRepo` — chúng
  không được dùng). Giữ nguyên.
* `update` sửa **tất cả** report khớp `(targetId, targetType)`, không phải một bản ghi.

### 26. Upload

* Multer `diskStorage` không set `destination` → ghi vào thư mục tạm của OS; `filename` giữ nguyên
  `originalname` (**đụng tên là ghi đè**).
* Cloudinary: `resource_type: 'image'` cho ảnh, `'video'` cho video; lấy `secure_url`.
* Field name theo route: post `images`/`videos` (≤5), profile `avatar`/`cover` (≤1),
  group/comment/message/ads `image` (1).

### 31–32. Vận hành

* Log: winston, level từ `LOG_LEVEL`, console + file `logs/application-%DATE%.log`, giữ 14 ngày, 20MB.
* Biến môi trường: xem `example.env` — 25 biến, gồm DB, Redis, JWT, SMTP, Cloudinary, admin khởi tạo.
* `synchronize: true` — schema do TypeORM tự tạo từ entity, **không có migration file**.
