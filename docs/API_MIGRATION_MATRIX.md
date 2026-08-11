# API_MIGRATION_MATRIX

Toàn bộ endpoint tìm thấy trong `lopet-be` (Express 5 + TypeORM). Nguồn: `src/routes/index.ts`
và 14 file trong `src/routes/v1/`, đối chiếu tới tận controller/service để lấy hành vi thật.

Base path: **`/v1`** (mount trong `src/index.ts`: `route.use('/v1', router)`).

Ký hiệu cột **Auth**:

| Ký hiệu | Middleware TS | Nghĩa |
|---|---|---|
| `-` | không có | công khai, không đọc token |
| `opt` | `optionalAuth()` | không token → khách; token hỏng → **401**; token tốt → có danh tính |
| `req` | `verifyToken()` | bắt buộc token, thiếu → **400 BadRequest** (không phải 401) |

Cột **AuthZ** ghi tầng phân quyền: `perm:<code>` = `requirePermission`, `own:<...>` = `requireOwnership`,
`cap` = `requireApprovedAdvertiser`, `rel` = `requireFriendOrSelf`, `svc` = kiểm tra trong service.

> **Toàn bộ 4 route của authRouter được mount HAI lần**: `/v1/auth/*` và `/v1/password/*`
> (`router.use('/password', authRouter)`). Java phải giữ cả hai tiền tố.

---

## 1. Auth — `/v1/auth` **và** `/v1/password`

| # | Method | Endpoint | Auth | AuthZ | Body | Response `data` | Lỗi | Java |
|---|---|---|---|---|---|---|---|---|
| 1 | POST | `/v1/auth/login` | - | Joi `loginValidation` | `{username, password}` | `{id, accessToken, refreshToken}` | 404 `No user found`; 400 `Người dùng <username> đã bị khoá`; 400 `Mật khẩu không trùng khớp` | DONE |
| 2 | POST | `/v1/auth/signup` | - | Joi `registerValidation` | `{email, username, password, confirmPassword}` | `{id, email, username}` (201) | 400 `Bạn cần xác thực OTP trước khi đăng ký.`; 409 email/username trùng; 400 password≠confirm | DONE |
| 3 | POST | `/v1/auth/verify` | - | - | `{email, password}` | `{isValid: true}` | 404 `Không tim thấy tài khoản`; 400 `Sai mật khẩu` | DONE |
| 4 | POST | `/v1/auth/reset` | - | Joi `resetPasswordValidation` | `{email, password, confirmPassword}` | `{id, email, username}` | 400 `Mật khẩu xác nhận không khớp`; 403 `Bạn cần xác thực OTP trước khi đổi mật khẩu.`; 404 | DONE |
| 1b–4b | POST | `/v1/password/{login,signup,verify,reset}` | | | | | | DONE |

**Chi tiết nghiệp vụ**

* `login` đọc account bằng `findByUsernameForAuth` (`addSelect('account.password')` vì cột `password`
  khai `select:false`), nạp kèm `accountRoles.role`. `roles[]` trong payload JWT = tên các role.
  Response **không** chứa roles — client phải tự decode JWT.
* Thứ tự kiểm tra login: tồn tại → `isBanned == 1` → so khớp mật khẩu.
* `signup` đòi cờ Redis `email_verified:<email>` (do luồng OTP đặt), **xoá cờ bằng `del` trước khi
  kiểm tra trùng email/username** → nếu trùng thì cờ đã mất, phải xin OTP lại.
* `reset` tiêu thụ cờ bằng `redis.getDel` (nguyên tử) **trước khi** ghi mật khẩu — fail-closed.
* Access token TTL = `ACCESS_TOKEN_EXPIRES_IN` (giây, mặc định 3600); refresh 36000. Payload JWT:
  `{id, email, roles[]}` + `iat/exp`. **Không có endpoint refresh token** trong TS.

## 2. Email/OTP — `/v1/emails`

| # | Method | Endpoint | Auth | Body | Response | Lỗi | Java |
|---|---|---|---|---|---|---|---|
| 5 | POST | `/v1/emails` | - | `{email}` | `data: undefined`, msg `Send email successfully` | 400 nếu `otp:<email>` còn tồn tại (chống spam) | DONE |
| 6 | POST | `/v1/emails/verify` | - | `{email, otp}` | msg `Verify email successfully` | 400 `OTP đã hết hạn hoặc không tồn tại.` / `OTP không chính xác.` | DONE |

OTP 6 chữ số, Redis key `otp:<email>` TTL **120s**. Verify xoá `otp:` và đặt
`email_verified:<email>='true'` TTL **300s**.

## 3. Account — `/v1/accounts`

| # | Method | Endpoint | Auth | AuthZ | Ghi chú | Java |
|---|---|---|---|---|---|---|
| 7 | GET | `/v1/accounts` | req | `perm:account:read` | Trả **entity Accounts thô** (kèm profile, sentFriendRequests, receivedFriendRequests, accountRoles.role), **lọc bỏ tài khoản có role ADMIN** | DONE |
| 8 | GET | `/v1/accounts/:id` | req | - (chỉ cần đăng nhập) | `GetAccountOutputDTO {id,email,username,roles[],profile?}` | DONE |
| 9 | POST | `/v1/accounts/ban/:id` | req | `perm:account:ban` | set `isBanned=1`, trả `{id}` | DONE |
| 10 | POST | `/v1/accounts/unban/:id` | req | `perm:account:ban` | set `isBanned=0`, trả `{id}` | DONE |
| 11 | DELETE | `/v1/accounts/:id` | req | `perm:account:delete` | `repo.remove()` — **xoá cứng**, không phải soft delete | DONE |
| 12 | GET | `/v1/accounts/suggest/:id` | req | - | `:id` **bị bỏ qua**, dùng `req.user.id`; query `?limit=`; loại người đã có friendship bất kỳ trạng thái + chính mình + `isBanned=1`; `ORDER BY RAND()` | DONE |
| 13 | PUT | `/v1/accounts` | req | `perm:account:setRole` | body `{userId, roles: string[]}`; xoá sạch account_role cũ rồi ghi mới, `grantedBy` = người gọi | DONE |

## 4. Profile — `/v1/profiles`

| # | Method | Endpoint | Auth | AuthZ | Ghi chú | Java |
|---|---|---|---|---|---|---|
| 14 | GET | `/v1/profiles` | - | - | query `?fullName=` (ILIKE `%...%`), `?id=` | DONE |
| 15 | GET | `/v1/profiles/:id` | - | - | trả **entity Profiles** (kèm `account`) | DONE |
| 16 | GET | `/v1/profiles/accounts/:id` | - | - | theo accountId, trả `GetProfileOutputDTO` | DONE |
| 17 | POST | `/v1/profiles` | req | `perm:profile:update:own` | multipart `avatar[1]`, `cover[1]` → Cloudinary; **tạo profile rời, chưa gắn account** | DONE |
| 18 | POST | `/v1/profiles/:id` | req | `perm:profile:update:own` | gắn profile `:id` vào **account của người gọi** (từ token) | DONE |
| 19 | PATCH | `/v1/profiles/:id` | req | `perm:profile:update:own` + `own:profile.account.id` (**ADMIN không bypass**) | multipart avatar/cover; field rỗng → giữ giá trị cũ | DONE |

## 5. Group — `/v1/groups`

| # | Method | Endpoint | Auth | AuthZ | Ghi chú | Java |
|---|---|---|---|---|---|---|
| 20 | GET | `/v1/groups/suggest` | - | - | `ORDER BY RAND() LIMIT 10`, kèm members | DONE |
| 21 | GET | `/v1/groups/:id` | - | - | trả **entity Groups** kèm `members.account` (không lọc riêng tư) | DONE |
| 22 | GET | `/v1/groups/owned/:id` | - | - | group mà account `:id` có `group_members.role=OWNER` | DONE |
| 23 | GET | `/v1/groups/joined/:id` | - | - | mọi group account `:id` là thành viên | DONE |
| 24 | POST | `/v1/groups` | req | `perm:group:create` | multipart `image`; `type` != 'PUBLIC' → PRIVATE; người tạo thành `OWNER` | DONE |
| 25 | POST | `/v1/groups/invites` | req | `perm:group:update:own` + `svc: canManage` (OWNER/ADMIN) | body `{groupId, invitee}`; 400 nếu đã là thành viên | DONE |
| 26 | DELETE | `/v1/groups` | req | `perm:group:delete:own|group:delete` + `svc: isOwned` (**đúng OWNER**) | body `{groupId}` | DONE |
| 27 | DELETE | `/v1/groups/members` | req | `perm:group:update:own` + `svc: canManage` | body `{groupId, member}`; 400 nếu target là OWNER | DONE |
| 28 | PUT | `/v1/groups/:id` | req | `perm:group:update:own` + `svc: canManage` | multipart `image`. **Defect: `uploadedImage.secure_url` đọc vô điều kiện → thiếu file là 500** | DONE |

## 6. Post — `/v1/posts`

| # | Method | Endpoint | Auth | AuthZ | Ghi chú | Java |
|---|---|---|---|---|---|---|
| 29 | GET | `/v1/posts/suggest` | opt | visibility filter | 10 bài mới nhất người xem được phép thấy | DONE |
| 30 | GET | `/v1/posts` | opt | visibility filter | query `?content=` (LIKE), `?groupId=`; chuỗi `"undefined"` được coi là không truyền | DONE |
| 31 | GET | `/v1/posts/:id` | opt | visibility filter | không đủ quyền → **404** (không phải 403) | DONE |
| 32 | GET | `/v1/posts/accounts/:id` | opt | visibility filter | bài của account `:id` mà người xem thấy được | DONE |
| 33 | POST | `/v1/posts` | req | `perm:post:create` + `svc: resolveGroupForPost` | multipart `images[≤5]`, `videos[≤5]`; body `{content, groupId?, scope}` | DONE |
| 34 | PUT | `/v1/posts/:postId` | req | `perm:post:update:own` + `own` (**ADMIN không bypass**) | body `{content, scope, oldIdsMedia[]}` + media mới | DONE |
| 35 | DELETE | `/v1/posts/:id` | req | `perm:post:delete:own|post:delete` + `own` (ADMIN bypass) | xoá cứng | DONE |
| 36 | POST | `/v1/posts/like` | req | `svc`: bài phải visible | body `{postId}`; đã like → 200 `You have already liked this post` | DONE |
| 37 | POST | `/v1/posts/unlike` | req | `svc`: bài phải visible | body `{postId}`; chưa like → 200 `You have already unliked this post` | DONE |

**Quy tắc hiển thị bài (`postVisibility.visibleTo`) — 5 nhánh OR, mặc định ẨN:**

```
A. group_id IS NULL AND postScope = PUBLIC                          → mọi người, kể cả khách
B. group_id IS NOT NULL AND postScope = PUBLIC AND group.type=PUBLIC → mọi người, kể cả khách
--- dừng ở đây nếu viewerId undefined/NaN ---
C. author.id = viewerId                                             → mọi scope
D. group_id IS NULL AND postScope = FRIEND AND EXISTS(friend_ships ACCEPTED hai chiều)
E. group_id IS NOT NULL AND EXISTS(group_members có viewerId)       → mọi scope trong nhóm đó
```

**Quy tắc ghi (`postPolicy`):** `parseScope(scope, isGroupPost)` — bài cá nhân nhận
`PUBLIC|FRIEND|PRIVATE`, bài nhóm chỉ `PUBLIC|PRIVATE`; sai → 400. `isGroupPost` luôn suy ra từ
group **thật đã nạp từ DB**, không từ body. `resolveGroupForPost`: group không tồn tại → 404;
group PRIVATE mà không phải thành viên → 403; group PUBLIC → ai đăng nhập cũng đăng được.

## 7. Comment — `/v1/comments`

| # | Method | Endpoint | Auth | AuthZ | Ghi chú | Java |
|---|---|---|---|---|---|---|
| 38 | POST | `/v1/comments` | req | `perm:comment:create` + `svc`: bài phải visible | multipart `image`; body `{content, postId, replyCommentId?}`; comment cha **phải cùng post** | DONE |
| 39 | GET | `/v1/comments/:postId` | opt | visibility của bài | trả `{postId, comments[]}`, mỗi comment kèm account + profile; sắp xếp `createdAt DESC` | DONE |
| 40 | DELETE | `/v1/comments/:commentId` | req | `perm:comment:delete:own|post:delete` + `own` (ADMIN bypass; load qua bài để không lộ tồn tại) | | DONE |

## 8. FriendShip — `/v1/friendships`

| # | Method | Endpoint | Auth | AuthZ | Ghi chú | Java |
|---|---|---|---|---|---|---|
| 41 | GET | `/v1/friendships/:id` | req | `rel: chính chủ hoặc bạn ACCEPTED` (**ADMIN không bypass**) | bạn bè ACCEPTED của `:id` | DONE |
| 42 | GET | `/v1/friendships/send/:id` | req | - | `:id` **bị bỏ qua** → lời mời PENDING do người gọi gửi | DONE |
| 43 | GET | `/v1/friendships/receive/:id` | req | - | `:id` **bị bỏ qua** → lời mời PENDING người gọi nhận | DONE |
| 44 | POST | `/v1/friendships` | req | - | body `{receiverId}`; sender = token; trùng cặp (đúng chiều) → 400 | DONE |
| 45 | POST | `/v1/friendships/accept` | req | - | body `{senderId}`; receiver = token; → ACCEPTED | DONE |
| 46 | POST | `/v1/friendships/reject` | req | - | body `{senderId}`; → REJECTED | DONE |
| 47 | DELETE | `/v1/friendships` | req | - | body `{friendId}`; dò quan hệ **hai chiều** rồi xoá cứng | DONE |

Response chung của 41–43: `{me: {id, username, imageUrl, status?}, others: [...]}` — **không có email**.

## 9. Message — `/v1/messages`

| # | Method | Endpoint | Auth | AuthZ | Ghi chú | Java |
|---|---|---|---|---|---|---|
| 48 | GET | `/v1/messages/:id` | req | `own: sender hoặc receiver` (**bypassRoles rỗng — ADMIN cũng bị chặn**) | | DONE |
| 49 | PATCH | `/v1/messages/status/:id` | req | như trên | body `{status}` (SENT/DELIVERED/READ) | DONE |
| 50 | GET | `/v1/messages/me/:id` | req | - | **đối tượng hội thoại lấy từ query `?targetId=`**, `:id` bị bỏ qua; sắp xếp `createdAt DESC` | DONE |
| 51 | POST | `/v1/messages` | req | - | multipart `image`; body `{content, receiverId}`; **emit socket `chat messsage`** tới room `user_<receiverId>`; response trả lại chính input DTO | DONE |

## 10. Notification — `/v1/notifications`

| # | Method | Endpoint | Auth | AuthZ | Ghi chú | Java |
|---|---|---|---|---|---|---|
| 52 | POST | `/v1/notifications` | req | - | body `{receptorId, content, objectType}`; actor = token; **emit `notification`** tới `user_<receptorId>` | DONE |
| 53 | GET | `/v1/notifications/:id` | req | - (**không kiểm receptor**) | trả kèm actor/receptor + profile; `withDeleted: true` | DONE |
| 54 | GET | `/v1/notifications/me/:id` | req | - | `:id` **bị bỏ qua**, dùng token; `createdAt DESC` | DONE |
| 55 | PUT | `/v1/notifications/:id` | req | - (**không kiểm receptor**) | body `{status}`; **emit `change status`** tới room `object_<id>` | DONE |

Khoá trong response là **`notificationId`** (không phải `id`); REST dùng key `type`, socket dùng
`objectType` (create) và `type` (change status).

## 11. Report — `/v1/reports`

| # | Method | Endpoint | Auth | AuthZ | Ghi chú | Java |
|---|---|---|---|---|---|---|
| 56 | GET | `/v1/reports` | req | `perm:report:read` | query `?type=`, `?accountId=`, `?targetId=`; trả **entity Reports** kèm reporter, resolvedBy | DONE |
| 57 | POST | `/v1/reports` | req | `perm:report:create` | body `{targetId, type, reason}` | DONE |
| 58 | PUT | `/v1/reports/:targetId` | req | `perm:report:resolve` | body `{type, action}`; cập nhật **mọi** report cùng (targetId,type); ghi `resolvedBy`/`resolvedAt` | DONE |

## 12. Advertisement — `/v1/advertisements`

| # | Method | Endpoint | Auth | AuthZ | Ghi chú | Java |
|---|---|---|---|---|---|---|
| 59 | GET | `/v1/advertisements` | - | - | query `?accountId=` | DONE |
| 60 | GET | `/v1/advertisements/:id` | - | - | 404 `Không tìm thấy quảng cáo` | DONE |
| 61 | POST | `/v1/advertisements` | req | `perm:ads:create` + `cap` + Joi + multipart | **Defect: Joi validate chạy TRƯỚC `upload.single()` nên multipart luôn 400** | DONE |
| 62 | PUT | `/v1/advertisements/:adsId` | req | `perm:ads:update:own` + `own` (ADMIN bypass) | multipart `image` **bắt buộc** (đọc `image.path` vô điều kiện) | DONE |
| 63 | DELETE | `/v1/advertisements/:id` | req | `perm:ads:delete:own` + `own` | xoá cứng | DONE |

## 13. Advertiser — `/v1/advertisers`

| # | Method | Endpoint | Auth | AuthZ | Ghi chú | Java |
|---|---|---|---|---|---|---|
| 64 | POST | `/v1/advertisers` | req | `perm:advertiser:register` | body `{companyName}`; luôn tạo PENDING; đã có → 409 | DONE |
| 65 | GET | `/v1/advertisers/me` | req | - | 404 `Bạn chưa có hồ sơ nhà quảng cáo` | DONE |
| 66 | GET | `/v1/advertisers` | req | `perm:advertiser:read` | `createdAt DESC` | DONE |
| 67 | PUT | `/v1/advertisers/:id/status` | req | `perm:advertiser:approve` | body `{status}`; ghi `approvedBy`/`approvedAt` | DONE |

## 14. Role — `/v1/roles`

| # | Method | Endpoint | Auth | AuthZ | Ghi chú | Java |
|---|---|---|---|---|---|---|
| 68 | GET | `/v1/roles` | req | - | `[{id, name}]` | DONE |

---

## 15. Socket.IO (namespace mặc định `/`)

| Chiều | Event | Payload | Điều kiện | Java |
|---|---|---|---|---|
| handshake | `auth.token` | JWT access token | thiếu → `BadRequest`; hết hạn → `TOKEN_EXPIRED`; sai → `INVALID_TOKEN` | DONE |
| server | (connection) | tự `join('user_<userId>')` | | DONE |
| client→server | `join room` | `room: string` | join thêm room tuỳ ý | DONE |
| client→server | `disconnect` | | chỉ log | DONE |
| server→client | `chat messsage` *(sic — 3 chữ s)* | `{message, from}` → room `user_<receiverId>` | khi POST `/v1/messages` | DONE |
| server→client | `notification` | `{actorId, receptorId, content, objectType, status, createdAt}` → `user_<receptorId>` | khi POST `/v1/notifications` | DONE |
| server→client | `change status` | `{notificationId, actorId, receptorId, content, type, status, createdAt}` → room `object_<id>` | khi PUT `/v1/notifications/:id` | DONE |

CORS socket: `origin:'*'`, methods GET/POST/PUT/DELETE/PATCH.

---

## 16. Định dạng response

**Thành công** (`sendResponse`):
```json
{ "statusCode": 200, "message": "…", "data": … }
```

**Lỗi nghiệp vụ** (`globalExceptionMiddleware`) — **không có trường `data`**:
```json
{ "statusCode": 404, "message": "…" }
```

**Lỗi validation Joi** (`validate`) — dạng thứ ba, có `errors`:
```json
{ "statusCode": 400, "message": "Validation error",
  "errors": [{ "field": "email", "message": "\"email\" must be a valid email" }] }
```

Lỗi không phải `HttpError` (ví dụ `new Error(...)` trong MessageService/NotificationService) →
`error.code` undefined → HTTP **500** với message gốc.

**Tổng: 68 route handler, 72 đường dẫn (do authRouter mount 2 lần), 7 sự kiện socket.**
