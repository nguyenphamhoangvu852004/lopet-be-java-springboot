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
| 1 | POST | `/v1/auth/login` | - | Joi `loginValidation` | `{username, password}` | `{id, accessToken}` + `Set-Cookie: refreshToken` (HttpOnly) | 404 `No user found`; 400 `Người dùng <username> đã bị khoá`; 400 `Mật khẩu không trùng khớp` | DONE |
| 2 | POST | `/v1/auth/signup` | - | Joi `registerValidation` | `{email, username, password, confirmPassword}` | `{id, email, username}` (201) | 400 `Bạn cần xác thực OTP trước khi đăng ký.`; 409 email/username trùng; 400 password≠confirm | DONE |
| 3 | POST | `/v1/auth/verify` | - | - | `{email, password}` | `{isValid: true}` | 404 `Không tim thấy tài khoản`; 400 `Sai mật khẩu` | DONE |
| 4 | POST | `/v1/auth/reset` | - | Joi `resetPasswordValidation` | `{email, password, confirmPassword}` | `{id, email, username}` | 400 `Mật khẩu xác nhận không khớp`; 403 `Bạn cần xác thực OTP trước khi đổi mật khẩu.`; 404 | DONE |
| 5 | POST | `/v1/auth/refresh` | - | - | Cookie `refreshToken` (không có body) | `{id, accessToken}` + `Set-Cookie: refreshToken` (token đã xoay vòng) | 401 `Refresh token đã hết hạn`; 401 `Refresh token không hợp lệ`; 401 `Người dùng <username> đã bị khoá` | **MỚI** |
| 1b–5b | POST | `/v1/password/{login,signup,verify,reset,refresh}` | | | | | | DONE |

**Chi tiết nghiệp vụ**

* `login` đọc account bằng `findByUsernameForAuth` (`addSelect('account.password')` vì cột `password`
  khai `select:false`), nạp kèm `accountRoles.role`. `roles[]` trong payload JWT = tên các role.
  Response **không** chứa roles — client phải tự decode JWT.
* Thứ tự kiểm tra login: tồn tại → `isBanned == 1` → so khớp mật khẩu.
* `refresh` **không có ở bản TypeScript** — bên đó login trả `refreshToken` nhưng không route nào
  nhận lại, tức access token hết hạn là đăng xuất cứng. Endpoint mới ký lại cả hai token sau khi
  đọc lại roles từ DB; tài khoản `isBanned == 1` bị chặn ngay tại đây (401, không phải 400 như
  login) để client biết đường xoá phiên. Chi tiết: `AUTHENTICATION_AUTHORIZATION.md` §3.6.
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
| 17 | GET | `/v1/profiles/me` | req | - | hồ sơ của chính người gọi; accountId từ token | DONE |
| 18 | PUT | `/v1/profiles` | req | `perm:profile:update:own` | multipart `avatar[1]`, `cover[1]` → Cloudinary; hồ sơ tra bằng **accountId từ token**, không có path param | DONE |

> **Đổi so với bản TypeScript** (không phải lệch port — là refactor có chủ ý, xem mục 4.8 của
> MIGRATION_FINAL_REPORT):
>
> - Bỏ `POST /v1/profiles` (tạo hồ sơ rời) và `POST /v1/profiles/:id` (gắn hồ sơ vào account).
>   Endpoint thứ hai không kiểm sở hữu của `:id` nên gắn được hồ sơ của người khác vào tài khoản mình.
> - Bỏ `PATCH /v1/profiles/:id`, thay bằng `PUT /v1/profiles`.
> - Hồ sơ được cấp sẵn lúc tạo tài khoản (`ProfileFactory.seedFor`), người dùng không tự tạo.
> - Không còn endpoint ghi nào nhận `profileId` → `ProfileAccessGuard` đã bị xoá, module này không
>   còn tầng ownership.

## 5. Group — `/v1/groups`

| # | Method | Endpoint | Auth | AuthZ | Ghi chú | Java |
|---|---|---|---|---|---|---|
| 20 | GET | `/v1/groups/suggest` | - | - | `ORDER BY RAND() LIMIT 10`, kèm members | DONE |
| 21 | GET | `/v1/groups/:id` | opt | lọc riêng tư nhóm | nhóm PRIVATE + người ngoài → `members: []`, `restricted: true`; metadata vẫn công khai. Thêm `totalMembers`, `viewerStatus` | DONE |
| 22 | GET | `/v1/groups/owned/:id` | - | - | group mà account `:id` có `group_members.role=OWNER` | DONE |
| 23 | GET | `/v1/groups/joined/:id` | - | - | mọi group account `:id` là thành viên | DONE |
| 24 | POST | `/v1/groups` | req | `perm:group:create` | multipart `image`; `type` != 'PUBLIC' → PRIVATE; người tạo thành `OWNER` | DONE |
| 25 | POST | `/v1/groups/invites` | req | `@RequirePet` + `svc: requireActiveMember` | body `{groupId, invitee}`. **Nay tạo LỜI MỜI PENDING**, không thêm thẳng; mọi thành viên ACTIVE mời được; 409 nếu đã có hàng | DONE |
| 26 | DELETE | `/v1/groups` | req | `perm:group:delete:own|group:delete` + `svc: isOwned` (**đúng OWNER**) | body `{groupId}` | DONE |
| 27 | DELETE | `/v1/groups/members` | req | `perm:group:update:own` + `svc: canManage` | body `{groupId, member}`; 400 nếu target là OWNER | DONE |
| 28 | PUT | `/v1/groups/:id` | req | `perm:group:update:own` + `svc: canManage` | multipart `image`. **Defect: `uploadedImage.secure_url` đọc vô điều kiện → thiếu file là 500** | DONE |
| 28b | POST | `/v1/groups/:id/join` | req | `@RequirePet` | PUBLIC → ACTIVE ngay; PRIVATE → PENDING chờ duyệt. Đang được mời thì coi như chấp nhận. 409 nếu đã là thành viên | NEW |
| 28c | DELETE | `/v1/groups/:id/join` | req | - | huỷ yêu cầu do chính mình gửi | NEW |
| 28d | DELETE | `/v1/groups/:id/leave` | req | `@RequirePet` | 400 nếu là OWNER | NEW |
| 28e | GET | `/v1/groups/:id/requests` | req | `perm:group:update:own` + `svc: requireManager` | chỉ yêu cầu tự gửi (`invited_by` null), không lẫn lời mời | NEW |
| 28f | POST | `/v1/groups/requests/approve`\|`/reject` | req | `perm:group:update:own` + `svc: requireManager` | body `{groupId, accountId}`; reject = xoá hàng | NEW |
| 28g | GET | `/v1/groups/invites/mine` | req | - | hộp thư lời mời của tài khoản đang đăng nhập | NEW |
| 28h | POST | `/v1/groups/invites/accept`\|`/reject` | req | `@RequirePet` | body `{groupId}`; chỉ chạm hàng có `invited_by` khác null | NEW |

**Cơ chế vào nhóm** (chi tiết: `docs/GROUP_MANAGEMENT.md`): `group_members` nhận thêm
`status enum('PENDING','ACTIVE')` và `invited_by`. `PENDING` + `invited_by` NULL = tự xin vào nhóm
PRIVATE, quản trị nhóm duyệt; `PENDING` + `invited_by` khác NULL = được mời, chính người được mời duyệt.
Từ chối = xoá hàng. **Mọi truy vấn phân quyền lọc `status = ACTIVE`.**


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
E. group_id IS NOT NULL AND EXISTS(group_members ACTIVE có viewerPetId) → mọi scope trong nhóm đó
```

**Quy tắc ghi (`postPolicy`):** `parseScope(scope, isGroupPost)` — bài cá nhân nhận
`PUBLIC|FRIEND|PRIVATE`, bài nhóm chỉ `PUBLIC|PRIVATE`; sai → 400. `isGroupPost` luôn suy ra từ
group **thật đã nạp từ DB**, không từ body. `resolveGroupForPost`: group không tồn tại → 404;
**không phải thành viên ACTIVE → 403 Ở CẢ HAI LOẠI NHÓM**. Trước đây nhóm PUBLIC cho ai đăng nhập cũng
đăng được mà không cần tham gia — đọc/thích/bình luận vẫn mở cho mọi người, chỉ ĐĂNG là hành động của
thành viên. Hàng PENDING không tính là thành viên.

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
| 49 | PATCH | `/v1/messages/status/:id` | req | như trên **+ chỉ RECEIVER** | body `{status}`; **không lùi trạng thái** (lùi → no-op, không lỗi) | DONE |
| 50 | GET | `/v1/messages/me/:id` | req | - | **đối tượng hội thoại lấy từ query `?targetId=`**, `:id` bị bỏ qua; sắp xếp `createdAt DESC` | DONE |
| 51 | POST | `/v1/messages` | req | - | multipart `image`; body `{content, receiverId}`; **đẩy realtime tới `/topic/user.<receiverId>/chat`**; response = input DTO **+ `id`, `status`, `createdAt`** | DONE |
| 51a | PATCH | `/v1/messages/delivered` | req | lọc trong query: chỉ tin có `receiver = token` | body `{messageIds: []}`; đường lui REST của `/app/message.delivered`; id lạ bị **bỏ qua âm thầm** | DONE |
| 51b | PATCH | `/v1/messages/read?partnerId=` | req | reader = token | đánh dấu READ **cả hội thoại** trong một UPDATE | DONE |
| 51c | GET | `/v1/messages/unread-count` | req | - | `{count}` — mọi tin `receiver = token` và `status <> READ` | DONE |

Trạng thái tin nhắn đi MỘT CHIỀU `SENT → DELIVERED → READ` (`MessageStatus.isAtLeast`). Bảng
`messages` có thêm `deliveredAt` / `readAt` — `status` chỉ giữ được trạng thái mới nhất nên không
đủ để hiện "đã nhận lúc … · đã xem lúc …". Migration: `scripts/message-status-migration.sql`.

## 10. Notification — `/v1/notifications`

| # | Method | Endpoint | Auth | AuthZ | Ghi chú | Java |
|---|---|---|---|---|---|---|
| 52 | POST | `/v1/notifications` | req | - | body `{receptorId, content, objectType}`; actor = token; **đẩy realtime tới `/topic/user.<receptorId>/notification`** | DONE |
| 53 | GET | `/v1/notifications/:id` | req | - (**không kiểm receptor**) | trả kèm actor/receptor + profile; `withDeleted: true` | DONE |
| 54 | GET | `/v1/notifications/me/:id` | req | - | `:id` **bị bỏ qua**, dùng token; `createdAt DESC` | DONE |
| 55 | PUT | `/v1/notifications/:id` | req | - (**không kiểm receptor**) | body `{status}`; **đẩy realtime tới `/topic/notification.<id>`** | DONE |

Khoá trong response là **`notificationId`** (không phải `id`); REST dùng key `type`, payload realtime
dùng `objectType` (kênh `notification`) và `type` (`/topic/notification.<id>`).

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

## 15. Realtime — STOMP over WebSocket (`/ws`)

Endpoint `ws(s)://<host>/ws` trên cùng cổng với REST. Prefix ứng dụng `/app`, broker `/topic`,
không dùng SockJS. Bảng ánh xạ đầy đủ từ 7 sự kiện Socket.IO cũ và snippet client:
[`docs/REALTIME_WEBSOCKET_MIGRATION.md`](REALTIME_WEBSOCKET_MIGRATION.md).

| Chiều | Frame / destination | Payload | Điều kiện | Java |
|---|---|---|---|---|
| handshake | `CONNECT` header `Authorization: Bearer <jwt>` | — | thiếu → `AUTHENTICATION_ERROR`; hết hạn → `TOKEN_EXPIRED`; sai → `INVALID_TOKEN`; trả về ở header `message` của frame `ERROR` | DONE |
| client→server | `SUBSCRIBE` | — | thay cho sự kiện `join room`; destination ngoài hợp đồng → `FORBIDDEN_DESTINATION` | DONE |
| server→client | `/topic/user.<receiverId>/chat` | `{message, from}` | khi POST `/v1/messages` | DONE |
| server→client | `/topic/user.<receptorId>/notification` | `{notificationId, actorId, receptorId, content, objectType, objectId, status, createdAt}` | khi POST `/v1/notifications` | DONE |
| server→client | `/topic/notification.<id>` | `{notificationId, actorId, receptorId, content, type, status, createdAt}` | khi PUT `/v1/notifications/:id` | DONE |
| client→server | `SEND /app/message.delivered` | `{messageIds: []}` | người nhận ack lô tin đã tới thiết bị | DONE |
| client→server | `SEND /app/message.read` | `{partnerId}` | người nhận mở hội thoại → READ toàn bộ | DONE |
| server→client | `/topic/user.<senderId>/message-status` | `{messageIds, status, at, byUserId}` | sau hai frame trên và sau `PATCH /status`, `/delivered`, `/read` | DONE |

Ba destination trạng thái là phần THÊM VÀO của bản Java, bản Express không có. Ghi chú triển khai:

- Danh tính của hai frame client→server lấy từ `Principal` của phiên (do `StompAuthChannelInterceptor`
  gắn ở frame CONNECT), **không bao giờ từ payload** — xem `MessageStompController`.
- `message-status` gộp theo NGƯỜI GỬI: một lần đọc cả hội thoại chỉ tốn một frame cho mỗi người,
  và không ai thấy id tin nhắn của người khác.
- Sự kiện chỉ rời server SAU khi transaction commit (service trả `StatusUpdateResult`, caller mới gọi
  `MessageStatusNotifier`) — bắn từ trong transaction thì người gửi có thể thấy "đã xem" của một
  transaction sắp rollback.
- Mọi destination server→client dựng qua `RealtimeGateway`; chiều ngược lại là `@MessageMapping` nằm
  trong chính module nghiệp vụ, nên `realtime` không phụ thuộc ngược vào `message`/`notification`.

Bảo mật destination (bản Socket.IO KHÔNG có, vì `join room` nhận tên phòng tuỳ ý):

- `/topic/user.<id>/**` chỉ chính chủ `<id>` subscribe được;
- `/topic/notification.<id>` cần đã xác thực, không cần là người nhận — giữ đúng phòng `object_<id>`;
- client chỉ `SEND` được vào `/app/**`; gửi thẳng vào `/topic/**` bị chặn, nếu không thì ai cũng
  giả được tin nhắn của người khác.

CORS WebSocket: dùng chung `lopet.cors.allowed-origins` với REST.

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

**Tổng: 68 route handler, 72 đường dẫn (do authRouter mount 2 lần), 8 destination realtime.**
