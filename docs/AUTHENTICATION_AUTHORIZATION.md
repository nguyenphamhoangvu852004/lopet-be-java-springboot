# AUTHENTICATION & AUTHORIZATION

Tài liệu này mô tả **CURRENT IMPLEMENTATION** — trạng thái code thật trong repository tại nhánh
`dev`. Mọi khẳng định về hành vi đều kèm `File:Line`. Chỗ nào không đọc được từ source code sẽ ghi
`Unable to determine from current implementation.` Các đề xuất nằm riêng ở §18–§20 dưới nhãn
**RECOMMENDED DESIGN** và **chưa** được áp dụng vào code.

> Cảnh báo về tài liệu cũ: `docs/ARCHITECTURE.md` §1 ghi `Auth: jjwt + Spring Security filter` và
> `docs/MIGRATION_FEATURE_MATRIX.md` liệt kê `security/authz/FriendOrSelfGuard`,
> `security/authz/ApprovedAdvertiserGuard`. **Cả ba đều không tồn tại trong code hiện tại**: không
> có dependency jjwt trong `pom.xml`, không có hai class guard đó. Xem §18 — `DOC-1`.

---

## 1. Security Architecture Overview

### 1.1 Danh sách file thực tế

Toàn bộ file liên quan tới authentication/authorization tìm thấy trong repository:

| File | Package | Vai trò |
|---|---|---|
| `SecurityConfig.java` | `com.nguyenvu.lopet.security` | `SecurityFilterChain` + `PasswordEncoder` bean |
| `Auth.java` | `com.nguyenvu.lopet.security` | Annotation đánh dấu route cần/không cần token |
| `AuthInterceptor.java` | `com.nguyenvu.lopet.security` | `HandlerInterceptor` áp dụng ngữ nghĩa của `@Auth` |
| `RequirePermission.java` | `com.nguyenvu.lopet.security` | Annotation khai báo permission cần có |
| `RequirePermissionAspect.java` | `com.nguyenvu.lopet.security` | AspectJ `@Before` thực thi `@RequirePermission` |
| `CurrentUser.java` | `com.nguyenvu.lopet.security` | Truy cập danh tính từ `SecurityContextHolder` |
| `jwt/JwtService.java` | `com.nguyenvu.lopet.security.jwt` | Sinh + kiểm JWT HS256 (tự hiện thực bằng JCA) |
| `jwt/JwtAuthenticationFilter.java` | `com.nguyenvu.lopet.security.jwt` | `OncePerRequestFilter` đọc token, nạp SecurityContext |
| `jwt/UserPrincipal.java` | `com.nguyenvu.lopet.security.jwt` | `record(Integer id, String email, List<String> roles)` |
| `jwt/JwtException.java` | `com.nguyenvu.lopet.security.jwt` | Lỗi giải mã token, mang cờ `expired` |
| `authz/PermissionCatalog.java` | `com.nguyenvu.lopet.security.authz` | Danh mục permission + ánh xạ role→permission (hard-code) |
| `authz/PermissionDef.java` | `com.nguyenvu.lopet.security.authz` | `record` mô tả một permission |
| `authz/OwnershipGuard.java` | `com.nguyenvu.lopet.security.authz` | Kiểm "đúng tài nguyên của mình không" |
| `role/entity/RoleName.java` | `com.nguyenvu.lopet.role.entity` | `enum {ADMIN, MODERATOR, SUPPORT}` |
| `role/entity/Role.java` | `com.nguyenvu.lopet.role.entity` | Entity `roles`, `@ManyToMany` tới `permissions` |
| `role/entity/Permission.java` | `com.nguyenvu.lopet.role.entity` | Entity `permissions` |
| `role/entity/PermissionScope.java` | `com.nguyenvu.lopet.role.entity` | `enum {ANY, OWN}` |
| `account/entity/Account.java` | `com.nguyenvu.lopet.account.entity` | Entity `accounts` (email/username/password/isBanned) |
| `account/entity/AccountRole.java` | `com.nguyenvu.lopet.account.entity` | Bảng nối `account_role` + cột audit |
| `auth/AuthService.java` | `com.nguyenvu.lopet.auth` | login / register / resetPassword / verifyAccount |
| `auth/AuthController.java` | `com.nguyenvu.lopet.auth` | 4 endpoint, mount 2 tiền tố |
| `config/WebConfig.java` | `com.nguyenvu.lopet.config` | Đăng ký `AuthInterceptor` + cấu hình CORS |
| `common/exception/*.java` | `com.nguyenvu.lopet.common.exception` | `HttpException` + `GlobalExceptionHandler` |
| `realtime/SocketIoConfig.java` | `com.nguyenvu.lopet.realtime` | Xác thực Socket.IO qua `AuthTokenListener` |
| `realtime/SocketIoServerRunner.java` | `com.nguyenvu.lopet.realtime` | Vòng đời socket + watchdog kết nối chưa xác thực |
| `bootstrap/AuthorizationSeeder.java` | `com.nguyenvu.lopet.bootstrap` | Seed `permissions`/`roles`/`role_permission` |
| `bootstrap/AdminInitializer.java` | `com.nguyenvu.lopet.bootstrap` | Tạo + cấp ADMIN cho tài khoản `INIT_ADMIN_*` |
| `post/PostAccessGuard.java` | `com.nguyenvu.lopet.post` | Ownership cho bài viết |
| `comment/CommentAccessGuard.java` | `com.nguyenvu.lopet.comment` | Ownership cho bình luận |
| `message/MessageAccessGuard.java` | `com.nguyenvu.lopet.message` | Kiểm người gọi là sender/receiver |
| `profile/ProfileAccessGuard.java` | `com.nguyenvu.lopet.profile` | Ownership cho hồ sơ |
| `advertisement/AdvertisementAccessGuard.java` | `com.nguyenvu.lopet.advertisement` | Ownership cho quảng cáo |
| `advertiser/AdvertiserService.java` | `com.nguyenvu.lopet.advertiser` | `requireApproved()` — tầng capability |
| `post/repository/PostVisibility.java` | `com.nguyenvu.lopet.post.repository` | Mệnh đề SQL "ai xem được bài nào" |
| `post/PostPolicy.java` | `com.nguyenvu.lopet.post` | Quy tắc phía ghi cho bài viết/nhóm |
| `email/OtpStore.java`, `email/EmailService.java` | `com.nguyenvu.lopet.email` | OTP — điều kiện của register + reset password |

**Các class KHÔNG tồn tại trong repository này** (đều là thành phần Spring Security tiêu chuẩn, được
liệt kê ở đây vì câu hỏi yêu cầu tìm chúng):

`UserDetailsService`, `UserDetails`, `AuthenticationProvider`, `DaoAuthenticationProvider`,
`AuthenticationManager`, `AuthenticationEntryPoint`, `AccessDeniedHandler`,
`@EnableMethodSecurity`, `@PreAuthorize`, `@Secured`, `@RolesAllowed`, `RefreshTokenService`,
bảng `refresh_tokens`, bảng `sessions`.

→ `Not used in current implementation.` (kiểm chứng: `grep -rn "EnableMethodSecurity\|PreAuthorize\|Secured\|RolesAllowed\|UserDetailsService\|AuthenticationProvider\|AccessDeniedHandler\|AuthenticationEntryPoint" src/` chỉ khớp đúng một dòng **comment** ở `RequirePermission.java:15`).

### 1.2 Điểm mấu chốt của kiến trúc

`SecurityConfig.java:36` khai:

```java
.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
```

Nghĩa là **Spring Security không ra bất kỳ quyết định phân quyền nào**. Nó chỉ còn hai việc: chạy
`JwtAuthenticationFilter` và giữ `SecurityContextHolder`. Toàn bộ quyết định nằm ở **bốn tầng tự
viết**, chạy trong tầng Spring MVC (interceptor → aspect → controller → service):

```text
Tầng 0  AUTHENTICATION   @Auth                 → AuthInterceptor        (có token hợp lệ không)
Tầng 1  PERMISSION       @RequirePermission    → RequirePermissionAspect(hành động này có được phép)
Tầng 2  OWNERSHIP        *AccessGuard          → OwnershipGuard         (đúng tài nguyên của mình)
Tầng 3  CAPABILITY       AdvertiserService     → requireApproved()      (có tư cách chạy ads chưa)
Tầng 3' RELATIONSHIP     FriendshipService     → areFriends()           (có quan hệ gì với chủ TN)
Tầng 3''VISIBILITY       PostVisibility        → mệnh đề SQL            (đọc được bài nào)
```

Lý do được ghi ngay trong javadoc `SecurityConfig.java:16-21`: dùng thêm `authorizeHttpRequests`
theo pattern URL sẽ tạo ra tầng thứ năm và hai tầng có thể nói khác nhau.

### 1.3 Component nào gọi component nào

```text
HTTP Request
  │
  ├─ springSecurityFilterChain (DelegatingFilterProxy)
  │    └─ JwtAuthenticationFilter.doFilterInternal()      ← JwtAuthenticationFilter.java:41
  │         ├─ extractToken(request)                      ← :76
  │         ├─ JwtService.parseAccessToken(token)         ← JwtService.java:70
  │         ├─ SecurityContextHolder.setAuthentication()  ← :56
  │         └─ request.setAttribute("lopet.jwt.state")    ← :46/:58/:60
  │
  ├─ DispatcherServlet
  │    ├─ CORS (Spring MVC CorsProcessor, cấu hình ở WebConfig.java:31)
  │    ├─ AuthInterceptor.preHandle()                     ← AuthInterceptor.java:27
  │    │    └─ đọc @Auth + request attribute → 400 / 500 / 401 / đi tiếp
  │    │
  │    ├─ RequirePermissionAspect.check()                 ← RequirePermissionAspect.java:19
  │    │    ├─ CurrentUser.optional()                     ← CurrentUser.java:17
  │    │    └─ PermissionCatalog.resolvePermissions/hasPermission ← PermissionCatalog.java:72/:94
  │    │
  │    └─ Controller method
  │         ├─ <X>AccessGuard.require*()  → OwnershipGuard.check()  ← OwnershipGuard.java:45
  │         ├─ AdvertiserService.requireApproved()                  ← AdvertiserService.java:97
  │         ├─ FriendshipService.areFriends()                       ← FriendshipController.java:43
  │         └─ Service → Repository (PostVisibility.VISIBLE_TO trong câu JPQL)
  │
  └─ GlobalExceptionHandler (@RestControllerAdvice)       ← GlobalExceptionHandler.java:29
```

### 1.4 Trả lời trực tiếp các câu hỏi kiến trúc

| Câu hỏi | Trả lời | Nguồn |
|---|---|---|
| JWT được tạo ở đâu? | `JwtService.sign()`, gọi từ `AuthService.login()` — **chỉ ở đúng một chỗ** | `JwtService.java:74`, `AuthService.java:58-59` |
| JWT được verify ở đâu? | Hai nơi độc lập: `JwtAuthenticationFilter` (HTTP) và `SocketIoConfig.authenticate` (WebSocket) | `JwtAuthenticationFilter.java:52`, `SocketIoConfig.java:120` |
| User identity lưu ở đâu? | `SecurityContextHolder` (ThreadLocal) → `UsernamePasswordAuthenticationToken.principal` là một `UserPrincipal` | `JwtAuthenticationFilter.java:56-57` |
| Role/Authority lấy từ đâu? | **Từ claim `roles` trong JWT**, không bao giờ đọc lại DB trong luồng request | `JwtService.java:123`, `PermissionCatalog.java:72` |
| Access decision thực hiện ở đâu? | `AuthInterceptor.preHandle` (authn), `RequirePermissionAspect` (permission), các `*AccessGuard` (ownership), service (capability/relationship), repository (visibility) | xem §1.2 |
| Spring Security tham gia quyết định không? | **Không.** `permitAll` cho mọi request | `SecurityConfig.java:36` |

---

## 2. Authentication Flow

### 2.1 Luồng đăng nhập (thực tế)

```text
Client
  │  POST /v1/auth/login   (hoặc /v1/password/login — cùng controller)
  │  body: {"username": "...", "password": "..."}
  ▼
JwtAuthenticationFilter.doFilterInternal()      ← không có Authorization header → state=ABSENT, đi tiếp
  ▼
AuthInterceptor.preHandle()                     ← handler KHÔNG có @Auth → return true ngay
  ▼
AuthController.login(@Valid LoginRequest)       ← AuthController.java:37
  │  Bean Validation: username NotBlank, password NotBlank + min 6 ký tự
  ▼
AuthService.login(request)                      ← AuthService.java:45   @Transactional(readOnly=true)
  │
  ├─ AccountRepository.findDetailByUsername()   ← AccountRepository.java:29
  │     @EntityGraph{profile, accountRoles, accountRoles.role}
  │     rỗng → NotFoundException("No user found")            → HTTP 404
  │
  ├─ if (account.getIsBanned() == 1)
  │     → BadRequestException("Người dùng <username> đã bị khoá")  → HTTP 400
  │
  ├─ PasswordEncoder.matches(raw, account.getPassword())     ← AuthService.java:52
  │     false → BadRequestException("Mật khẩu không trùng khớp")   → HTTP 400
  │
  ├─ rolesOf(account) = accountRoles.stream().map(ar -> ar.getRole().getName().name())
  │                                                          ← AuthService.java:132-136
  ├─ new UserPrincipal(id, email, roles)
  ├─ JwtService.generateAccessToken(payload)                 ← secret ACCESS_TOKEN_SECRET
  └─ JwtService.generateRefreshToken(payload)                ← secret REFRESH_TOKEN_SECRET
  ▼
ApiResponse.ok(HttpStatusMessage.OK, LoginResponse)
  body: {"statusCode":200, "message":"OK", "data":{"id":.., "accessToken":"..", "refreshToken":".."}}
```

### 2.2 Trả lời 10 câu hỏi

| # | Câu hỏi | Trả lời thực tế |
|---|---|---|
| 1 | Client gửi request gì? | `POST /v1/auth/login` hoặc `POST /v1/password/login` — `AuthController.java:30` mount cả hai tiền tố |
| 2 | Controller nào nhận? | `com.nguyenvu.lopet.auth.AuthController.login()` — `AuthController.java:37` |
| 3 | DTO nào? | `LoginRequest(String username, String password)` — record, có `@NotNull/@NotBlank/@Size(min=6)` |
| 4 | Service nào xử lý? | `AuthService.login()` — `AuthService.java:45` |
| 5 | Repository nào? | `AccountRepository.findDetailByUsername()` — `AccountRepository.java:29` |
| 6 | Password verify ở đâu? | `AuthService.java:52` — `PasswordEncoder.matches()`, bean là `BCryptPasswordEncoder(10)` (`SecurityConfig.java:47`) |
| 7 | Authentication object tạo ở đâu? | **Không tạo lúc login.** Chỉ tạo ở mỗi request sau đó, trong `JwtAuthenticationFilter.java:57` (`new UsernamePasswordAuthenticationToken(...)`). Không có `AuthenticationManager` nào được gọi |
| 8 | JWT generate ở đâu? | `JwtService.sign()` — `JwtService.java:74`, gọi từ `AuthService.java:58-59` |
| 9 | JWT chứa claim gì? | Đúng 5 claim, đúng thứ tự: `id`, `email`, `roles`, `iat`, `exp` — `JwtService.java:77-82` |
| 10 | Response trả gì? | `{id, accessToken, refreshToken}` — `LoginResponse.java:7`. Cố ý **không** có `roles` |

### 2.3 Luồng của một request đã đăng nhập

```text
Client: Authorization: Bearer <accessToken>
  ▼
JwtAuthenticationFilter                 → parse OK → SecurityContext + state=VALID
  ▼
AuthInterceptor                         → @Auth(required=true), state=VALID → đi tiếp
  ▼
RequirePermissionAspect (nếu có)        → PermissionCatalog kiểm mã quyền
  ▼
Controller → *AccessGuard → Service → Repository
```

### 2.4 Register / Reset password / Verify

| Endpoint | Xác thực đầu vào | Ghi chú |
|---|---|---|
| `POST /v1/auth/signup` | **Không cần token.** Bắt buộc có cờ Redis `email_verified:<email>` (`AuthService.java:68`). Cờ bị `deleteVerifiedFlag` **trước** khi kiểm trùng email/username (`:72`) | Trùng email/username → 409; password≠confirm → 400 |
| `POST /v1/auth/reset` | **Không cần token.** Tiêu thụ cờ bằng `consumeVerifiedFlag` (GETDEL nguyên tử, `OtpStore.java:67`); thiếu cờ → `ForbiddenException` 403 (`AuthService.java:109`) | Ghi `passwordEncoder.encode(...)` vào `accounts.password` |
| `POST /v1/auth/verify` | **Không cần token.** Nhận `{email, password}`, trả `{isValid:true}` nếu đúng mật khẩu | `AuthService.java:122-130`. Xem §18 `SEC-4` |
| `POST /v1/auth/refresh` | **Không cần token.** Nhận `{refreshToken}` trong body; đọc lại tài khoản từ DB, chặn tài khoản bị khoá, trả cặp token mới | `AuthService.refresh`. Xem §3.6 |

**Logout:** `Not used in current implementation.` `SecurityConfig.java:34` gọi `logout(logout -> logout.disable())`; không có endpoint logout, không có blacklist token.

**Session:** `Not used in current implementation.` — `SessionCreationPolicy.STATELESS` (`SecurityConfig.java:35`).

---

## 3. JWT Implementation

### 3.1 Cấu hình

| Thông số | Giá trị | Nguồn |
|---|---|---|
| Property access secret | `lopet.jwt.access-token-secret` ← env `ACCESS_TOKEN_SECRET` | `application.yml`, `JwtService.java:47` |
| Property refresh secret | `lopet.jwt.refresh-token-secret` ← env `REFRESH_TOKEN_SECRET` | `application.yml`, `JwtService.java:48` |
| Access TTL | `lopet.jwt.access-token-expires-in` ← env `ACCESS_TOKEN_EXPIRES_IN`, **đơn vị giây**, `.env.example` = `3600` | `JwtService.java:49`, `:82` |
| Refresh TTL | `lopet.jwt.refresh-token-expires-in` ← env `REFRESH_TOKEN_EXPIRES_IN`, `.env.example` = `36000` | `JwtService.java:50` |
| Algorithm | **HS256 cứng**, hằng `HMAC_SHA256 = "HmacSHA256"` | `JwtService.java:34`, `:136` |
| Header | Chuỗi base64url **hard-code**: `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9` = `{"alg":"HS256","typ":"JWT"}` | `JwtService.java:38` |
| Thư viện | **Không có.** Tự hiện thực bằng JCA (`javax.crypto.Mac`) + `java.util.Base64` | `JwtService.java:12-13`, `pom.xml` (comment giải thích) |

Lý do tự viết được ghi ở `JwtService.java:24-26` và trong comment `pom.xml`: jjwt/Nimbus từ chối khoá
HMAC ngắn hơn 256 bit theo RFC 7518 §3.2, còn `ACCESS_TOKEN_SECRET` đang chạy **chỉ dài 23 byte**
(`JwtServiceTest.java:20-23` khẳng định đúng con số này). Đây là một quyết định đánh đổi bảo mật lấy
tính thay thế trực tiếp — xem §18 `SEC-1`.

### 3.2 Claims

`JwtService.java:77-82` — dùng `LinkedHashMap` để giữ nguyên thứ tự khoá:

| Claim | Kiểu | Nguồn giá trị |
|---|---|---|
| `id` | number | `UserPrincipal.id()` = `accounts.id` |
| `email` | string | `UserPrincipal.email()` |
| `roles` | string[] | Tên các `RoleName` mà account đang giữ |
| `iat` | number (epoch giây) | `Instant.now().getEpochSecond()` |
| `exp` | number (epoch giây) | `iat + ttlSeconds` |

**Không có** `sub`, `iss`, `aud`, `nbf`, `jti`. Subject thực tế nằm ở claim tuỳ biến `id`, không phải
`sub` chuẩn. Issuer/Audience: `Not used in current implementation.`

### 3.3 Bảng trách nhiệm

| Component | Class/File | Responsibility |
|---|---|---|
| Token generation (access) | `JwtService.generateAccessToken()` — `JwtService.java:58` | Gọi `sign(payload, accessSecret, accessTtlSeconds)` |
| Token generation (refresh) | `JwtService.generateRefreshToken()` — `JwtService.java:62` | Gọi `sign(...)` với `refreshSecret` |
| Ký / dựng chuỗi | `JwtService.sign()` — `JwtService.java:74` | Dựng claims → base64url → `HEADER + "." + body + "." + hmac` |
| HMAC | `JwtService.hmac()` — `JwtService.java:134` | `Mac.getInstance("HmacSHA256")`; lỗi → `IllegalStateException` |
| Token validation + parsing | `JwtService.parse()` — `JwtService.java:89` | Tách 3 phần, so chữ ký, decode JSON, kiểm `exp` |
| Điểm vào validation (HTTP + socket) | `JwtService.parseAccessToken()` — `JwtService.java:70` | Chỉ dùng `accessSecret` |
| Token extraction (HTTP) | `JwtAuthenticationFilter.extractToken()` — `JwtAuthenticationFilter.java:76` | `header.split(" ")[1]` |
| Token extraction (Socket.IO) | `SocketIoConfig.extractToken()` — `SocketIoConfig.java:146` | Đọc `auth.token` (Map) hoặc chuỗi trần |
| Claim → principal | `JwtService.toAccountId/asString/toRoles` — `:145`, `:149`, `:157` | Ép kiểu phòng thủ, không ném lỗi |

**Không có JWT logic viết tay ở nơi nào khác:** `grep -rn "HmacSHA256" src/main/java` chỉ khớp
`JwtService.java`. Hai điểm *verify* (HTTP filter và Socket.IO listener) đều đi qua đúng một method
`parseAccessToken()` — đây là điểm mạnh của thiết kế hiện tại.

### 3.4 Validation: từng bước của `parse()`

`JwtService.java:89-124`, theo đúng thứ tự:

1. `token.split("\.", -1)` — không đúng 3 phần, hoặc phần 0 / phần 1 rỗng → `JwtException(MALFORMED)`.
2. Tính `expected = hmac(parts[0] + "." + parts[1], secret)`.
3. Base64url-decode `parts[2]` và `parts[1]`; `IllegalArgumentException` → `JwtException(MALFORMED)`.
4. `MessageDigest.isEqual(expected, actual)` — so sánh **thời gian hằng**; sai → `JwtException(INVALID_SIGNATURE)`.
5. Deserialize claims bằng Jackson 3 (`tools.jackson`); lỗi → `JwtException(MALFORMED)`.
6. `exp` là `Number` và `now >= exp` → `JwtException(EXPIRED, expired=true)`. **Không có clock skew.**
   Nếu claim `exp` **thiếu hoặc không phải số**, nhánh `instanceof` không khớp → token **không bao giờ
   hết hạn**. Xem §18 `SEC-6`.
7. Trả `new UserPrincipal(toAccountId(id), asString(email), toRoles(roles))`.

**Về algorithm confusion:** `parse()` **không đọc** header để chọn thuật toán — nó luôn tính lại HMAC
SHA-256. Vì vậy token `{"alg":"none"}` hay `{"alg":"RS256"}` vẫn phải khớp chữ ký HS256 mới qua được.
Đây là hành vi **an toàn**, dù đạt được bằng cách bỏ qua header chứ không phải bằng cách kiểm nó.

### 3.5 Ba message lỗi

`JwtException.java:15-19` — chuỗi đặt trùng nguyên văn thư viện `jsonwebtoken` bên TypeScript, vì
trên route `@Auth(required=true)` chuỗi này lọt thẳng ra response body:

| Hằng | Giá trị | Cờ `expired` |
|---|---|---|
| `JwtException.EXPIRED` | `"jwt expired"` | `true` |
| `JwtException.INVALID_SIGNATURE` | `"invalid signature"` | `false` |
| `JwtException.MALFORMED` | `"jwt malformed"` | `false` |

### 3.6 Refresh token

`POST /v1/auth/refresh` (`AuthController.refresh`) nhận `{refreshToken}` và trả về **cặp token
mới** `{id, accessToken, refreshToken}` — endpoint MỚI, bản TypeScript không có.

| Điểm | Hành vi | Vì sao |
|---|---|---|
| Khoá ký | `JwtService.parseRefreshToken()` dùng `refreshSecret` | Access token không dùng thay refresh token được, và ngược lại |
| Nguồn roles | Đọc lại `accounts` + `account_role` từ DB | Ký lại payload cũ thì thu quyền phải chờ hết hạn refresh token (10h) mới có hiệu lực |
| Tài khoản bị khoá | 401 (login là 400) | Client cần một mã khiến interceptor xoá phiên; đây cũng là cơ chế **thu hồi** duy nhất hiện có |
| Refresh token trả về | Luôn là token MỚI (xoay vòng) | Phiên trượt theo hoạt động thay vì bị cắt cứng sau 10h |
| Xác thực | Không mang `@Auth` | Người gọi tới đây chính vì access token của họ đã chết |

**Còn thiếu:** không lưu trạng thái, nên refresh token cũ vẫn dùng được tới khi hết hạn (không phát
hiện được tái sử dụng), và access token đã phát ra không thu hồi được trước hạn. Xem §18 `SEC-5`.

Test: `AuthServiceRefreshTest` (nghiệp vụ), `AuthControllerRefreshTest` (định tuyến + validate).

---

## 4. JWT Authentication Filter

```text
File:    src/main/java/com/nguyenvu/lopet/security/jwt/JwtAuthenticationFilter.java
Package: com.nguyenvu.lopet.security.jwt
Class:   JwtAuthenticationFilter extends OncePerRequestFilter   (@Component)
```

### 4.1 Điểm khác biệt quan trọng nhất

Filter này **không bao giờ từ chối request**. Nó chỉ *ghi nhận* trạng thái token vào request attribute
rồi cho đi tiếp (javadoc `:18-25`). Việc từ chối do `AuthInterceptor` làm, dựa trên `@Auth`. Hệ quả:
**một request mang token rác vẫn chạy tới controller nếu handler không có `@Auth`**.

Hai attribute là hợp đồng giữa filter và interceptor:

| Hằng | Giá trị chuỗi | Kiểu giá trị |
|---|---|---|
| `ATTRIBUTE_STATE` (`:31`) | `"lopet.jwt.state"` | `TokenState` = `ABSENT` / `INVALID` / `VALID` (`:34-36`) |
| `ATTRIBUTE_ERROR` (`:32`) | `"lopet.jwt.error"` | `JwtException` (chỉ set khi `INVALID`) |

### 4.2 Flow thực tế

```text
HTTP Request
    │
    ▼
extractToken(request) ── Authorization header ── null? ──► state = ABSENT ──► chain.doFilter() ──► return
    │ (có token)
    ▼
JwtService.parseAccessToken(token)
    ├── JwtException ──► state = INVALID, error = exception    (KHÔNG set SecurityContext)
    │
    └── UserPrincipal
            ├─ roles.map(r -> new SimpleGrantedAuthority("ROLE_" + r))
            ├─ SecurityContextHolder.getContext().setAuthentication(
            │        new UsernamePasswordAuthenticationToken(principal, null, authorities))
            └─ state = VALID
    │
    ▼
try { chain.doFilter() } finally { SecurityContextHolder.clearContext() }
    │
    ▼
DispatcherServlet → AuthInterceptor → RequirePermissionAspect → Controller
```

### Method: `doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain)`

`JwtAuthenticationFilter.java:41-69`

**Purpose:** Đọc token (nếu có), giải mã, nạp danh tính vào `SecurityContext`, và ghi lại trạng thái
token để tầng sau quyết định có chặn hay không.

**Input:**
- `request` — đọc header `Authorization`, **ghi** hai attribute trạng thái.
- `response` — filter này không đụng tới (không ghi status, không ghi body).
- `chain` — chuỗi filter còn lại.

**Output:** `void`. Tác dụng phụ: `SecurityContextHolder` + hai request attribute.

**Flow:**
1. `:43` gọi `extractToken(request)`.
2. `:45-49` token `null` → `state = ABSENT`, `chain.doFilter()`, **return sớm**.
   ⚠️ Nhánh này **không** có `finally { clearContext() }` — an toàn vì chưa set gì, nhưng hai nhánh
   không đối xứng, người đọc dễ tưởng là thiếu sót.
3. `:52` `jwtService.parseAccessToken(token)`.
4. `:53-55` map từng role thành `SimpleGrantedAuthority("ROLE_" + role)`.
5. `:56-57` `new UsernamePasswordAuthenticationToken(principal, null, authorities)` → set vào context.
   `credentials = null` (không giữ lại token thô). Constructor 3 tham số này tự đặt `authenticated = true`.
6. `:58` `state = VALID`.
7. `:59-62` bắt **chỉ** `JwtException` → `state = INVALID` + lưu exception. Exception khác (ví dụ
   `IllegalStateException` từ `hmac()`) **không bị bắt**, nổi lên `GlobalExceptionHandler` → HTTP 500.
8. `:64-68` `chain.doFilter()` trong `try`, `SecurityContextHolder.clearContext()` trong `finally`.

**Called by:** Servlet container, qua `springSecurityFilterChain`. Vị trí trong chuỗi khai ở
`SecurityConfig.java:37`:
`.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`.
Vì `formLogin` đã bị disable (`SecurityConfig.java:32`), `UsernamePasswordAuthenticationFilter` thực
tế **không có** trong chuỗi — dòng này chỉ còn tác dụng chỉ định vị trí tương đối.

**Calls:** `extractToken()` (private), `JwtService.parseAccessToken()`, `SecurityContextHolder`,
`HttpServletRequest.setAttribute()`, `FilterChain.doFilter()`.

**Important behavior:**
- Chạy cho **mọi** request kể cả route công khai (không override `shouldNotFilter`).
- Token hỏng ⇒ `SecurityContext` **rỗng**, y như khách vãng lai; khác biệt chỉ nằm ở attribute.
- `clearContext()` ở `:67` dư thừa với `SecurityContextHolderFilter` của Spring Security (vốn đã clear
  trong `finally` của chính nó) nhưng vô hại.
- `OncePerRequestFilter` mặc định **không** chạy lại trên async dispatch; context bị clear khi thread
  gốc thoát. Code hiện tại không có endpoint async nên chưa gặp vấn đề.

### Method: `extractToken(HttpServletRequest)`

`JwtAuthenticationFilter.java:76-86`

**Purpose:** Lấy phần token từ header `Authorization`.

**Input:** `request`.

**Output:** `String` token, hoặc `null` khi không lấy được.

**Flow:**
1. `:77` `request.getHeader("Authorization")`; `null` → trả `null`.
2. `:81` `header.split(" ")` — **không kiểm tra `parts[0]` có phải chuỗi `Bearer`**.
3. `:82` `parts.length < 2 || parts[1].isEmpty()` → trả `null`.
4. `:85` trả `parts[1]`.

**Called by:** `doFilterInternal()` (`:43`).

**Calls:** `HttpServletRequest.getHeader()`, `String.split()`.

**Important behavior:**
- Header `Authorization: <token>` (không tiền tố) → `parts.length == 1` → **coi như KHÔNG có token** →
  route `@Auth` trả **400 "Token not found"**, không phải 401. Javadoc `:71-74` xác nhận đây là chủ ý
  (bám hành vi `req.header('Authorization')?.split(' ')[1]` của bản TS).
- Header `Basic <token>` / `Foo <token>` cũng được nhận, vì scheme không bị kiểm. Không tạo lỗ hổng
  (chuỗi vẫn phải qua kiểm chữ ký) nhưng là kiểm tra lỏng.
- Header có hai dấu cách (`Bearer  x`) → `parts[1]` rỗng → trả `null` → 400.

### Method: "load user" / "create Authentication" / "set SecurityContext"

`Not used in current implementation` như method riêng. Filter dựng `UserPrincipal` **hoàn toàn từ
claim của token** (`JwtService.java:123`) và **không truy vấn database một lần nào**. Không có
`UserDetailsService`, không có `AuthenticationProvider`. Việc tạo `Authentication` và set context là
hai dòng inline `:56-57` trong `doFilterInternal`.

---

## 5. Spring Security Configuration

```text
File:    src/main/java/com/nguyenvu/lopet/security/SecurityConfig.java
Package: com.nguyenvu.lopet.security
Class:   SecurityConfig   (@Configuration, @RequiredArgsConstructor)
Methods: securityFilterChain(HttpSecurity), passwordEncoder()
```

Lưu ý: **không có** `@EnableWebSecurity` tường minh — bean `SecurityFilterChain` được kích hoạt qua
auto-configuration của Spring Boot (`spring-boot-starter-security` trong `pom.xml`).

### 5.1 Toàn bộ `SecurityFilterChain` (`:29-39`)

| Dòng | Cấu hình | Ý nghĩa thực tế |
|---|---|---|
| `:31` | `csrf(csrf -> csrf.disable())` | Không sinh/kiểm CSRF token. Hợp lý vì API stateless dùng bearer token, không dùng cookie phiên |
| `:32` | `formLogin(login -> login.disable())` | Không có trang `/login` mặc định, không có `UsernamePasswordAuthenticationFilter` |
| `:33` | `httpBasic(basic -> basic.disable())` | Không nhận `Authorization: Basic` |
| `:34` | `logout(logout -> logout.disable())` | Không có `/logout` |
| `:35` | `sessionManagement(...STATELESS)` | Không tạo `HttpSession`, không lưu `SecurityContext` giữa các request |
| `:36` | `authorizeHttpRequests(auth -> auth.anyRequest().permitAll())` | **Mọi URL đều qua tầng Spring Security** |
| `:37` | `addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)` | Chèn filter JWT |

**Không được cấu hình** (⇒ `Not used in current implementation.`):
`http.cors(...)`, `exceptionHandling(...)`, `authenticationProvider(...)`,
`authenticationManager(...)`, `AuthenticationEntryPoint`, `AccessDeniedHandler`, `rememberMe`,
`anonymous(...)` (mặc định vẫn bật), `headers(...)`.

> **CORS không đi qua Spring Security**: nó được khai bằng `WebMvcConfigurer.addCorsMappings()`
> (`WebConfig.java:31`), tức là xử lý ở tầng Spring MVC **sau** filter chain. Xem §13.

### 5.2 `PasswordEncoder` (`:45-48`)

```java
return new BCryptPasswordEncoder(10);
```

Bean duy nhất kiểu `PasswordEncoder`, được inject vào `AuthService` (`AuthService.java:34`) và
`AdminInitializer` (`AdminInitializer.java:30`). **Không phải** `DelegatingPasswordEncoder` — nghĩa
là không có tiền tố `{bcrypt}` trong DB và không hỗ trợ nhiều thuật toán song song.

### 5.3 Giải thích `authorizeHttpRequests(...)`

Chỉ có **đúng một rule**:

| Endpoint Pattern | HTTP Method | Authentication Required (theo Spring Security) | Required Role/Authority | Reason |
|---|---|---|---|---|
| `/**` (`anyRequest()`) | mọi method | **No** | *(không)* | `SecurityConfig.java:36` — chủ ý: giữ một nguồn sự thật duy nhất cho phân quyền, xem javadoc `:16-21` |

Vì bảng trên chỉ có một dòng, **ma trận phân quyền thật** của ứng dụng nằm ở annotation trên từng
handler. Đây là bảng đầy đủ, đọc trực tiếp từ các controller:

Ký hiệu: `req` = `@Auth` (mặc định `required=true`) · `opt` = `@Auth(required=false)` ·
`—` = không có `@Auth` (công khai)

| Endpoint | Method | Auth | `@RequirePermission` | Tầng bổ sung trong code | File |
|---|---|---|---|---|---|
| `/v1/auth/login`, `/v1/password/login` | POST | — | — | — | `AuthController.java:36` |
| `/v1/auth/signup`, `/v1/password/signup` | POST | — | — | cờ Redis `email_verified` | `:41` |
| `/v1/auth/verify`, `/v1/password/verify` | POST | — | — | so mật khẩu | `:47` |
| `/v1/auth/refresh`, `/v1/password/refresh` | POST | — | — | chữ ký refresh token + `isBanned` | `AuthController.refresh` |
| `/v1/auth/reset`, `/v1/password/reset` | POST | — | — | `consumeVerifiedFlag` | `:52` |
| `/v1/emails` | POST | — | — | chống spam: còn OTP thì từ chối | `EmailController.java:25` |
| `/v1/emails/verify` | POST | — | — | so OTP | `:31` |
| `/v1/accounts` | GET | req | `account:read` | lọc bỏ tài khoản ADMIN | `AccountController.java:33` |
| `/v1/accounts/{id}` | GET | req | — | **không có** | `:41` |
| `/v1/accounts/ban/{id}` | POST | req | `account:ban` | — | `:47` |
| `/v1/accounts/unban/{id}` | POST | req | `account:ban` | — | `:54` |
| `/v1/accounts/{id}` | DELETE | req | `account:delete` | xoá cứng | `:61` |
| `/v1/accounts/suggest/{id}` | GET | req | — | `{id}` bị bỏ qua, dùng id trong token | `:72` |
| `/v1/accounts` | PUT | req | `account:setRole` | `grantedBy` lấy từ token | `:84` |
| `/v1/roles` | GET | req | — | — | `RoleController.java:26` |
| `/v1/profiles` | GET | — | — | — | `ProfileController.java:38` |
| `/v1/profiles/{id}` | GET | — | — | — | `:45` |
| `/v1/profiles/accounts/{id}` | GET | — | — | — | `:50` |
| `/v1/profiles` (multipart) | POST | req | `profile:update:own` | — | `:57` |
| `/v1/profiles` (json) | POST | req | `profile:update:own` | — | `:74` |
| `/v1/profiles/{id}` | POST | req | `profile:update:own` | accountId từ token | `:85` |
| `/v1/profiles/{id}` (multipart) | PATCH | req | `profile:update:own` | `ProfileAccessGuard.requireOwner` (NO_BYPASS) | `:99` |
| `/v1/profiles/{id}` (json) | PATCH | req | `profile:update:own` | `ProfileAccessGuard.requireOwner` | `:118` |
| `/v1/posts/suggest` | GET | opt | — | `PostVisibility` | `PostController.java:46` |
| `/v1/posts` | GET | opt | — | `PostVisibility` | `:53` |
| `/v1/posts/{id}` | GET | opt | — | `PostVisibility` | `:62` |
| `/v1/posts/accounts/{id}` | GET | opt | — | `PostVisibility` | `:68` |
| `/v1/posts` (multipart/json) | POST | req | `post:create` | `PostPolicy.resolveGroupForPost` + `parseScope` | `:77`, `:92` |
| `/v1/posts/{postId}` (multipart/json) | PUT | req | `post:update:own` | `PostAccessGuard.requireOwnerToEdit` (**NO_BYPASS**) + kiểm lại trong `PostService.update` | `:107`, `:123` |
| `/v1/posts/{id}` | DELETE | req | `post:delete:own` **hoặc** `post:delete` | `PostAccessGuard.requireOwnerToDelete` (bypass ADMIN) | `:135` |
| `/v1/posts/like`, `/v1/posts/unlike` | POST | req | — | — | `:143`, `:150` |
| `/v1/pets` | POST | req | `pet:create` | người tạo thành PRIMARY_OWNER, id lấy từ token | `PetController.java:46` |
| `/v1/pets/me` | GET | req | — | ownerId lấy từ token, **không** nhận query param | `:62` |
| `/v1/pets/{petId}` | GET | opt | — | `PetVisibilityFilter` | `:72` |
| `/v1/pets/{petId}` | PUT | req | `pet:update:own` | `PetAccessGuard.requireOwnerToEdit` (**NO_BYPASS**) + kiểm lại trong `PetService.update` | `:78` |
| `/v1/pets/{petId}` | DELETE | req | `pet:delete:own` | `PetAccessGuard.requireOwnerToArchive` (**NO_BYPASS**) + `PetService.archive` đòi đúng PRIMARY_OWNER | `:89` |
| `/v1/comments` (multipart/json) | POST | req | `comment:create` | `postRepository.findVisibleById` trong service | `CommentController.java:35`, `:48` |
| `/v1/comments/{postId}` | GET | opt | — | `PostVisibility` | `:68` |
| `/v1/comments/{commentId}` | DELETE | req | `comment:delete:own` **hoặc** `post:delete` | `CommentAccessGuard` (bypass ADMIN) | `:75` |
| `/v1/groups/suggest`, `/{id}`, `/owned/{id}`, `/joined/{id}` | GET | — | — | — | `GroupController.java:45-62` |
| `/v1/groups` (multipart/json) | POST | req | `group:create` | — | `:66`, `:79` |
| `/v1/groups/invites` | POST | req | `group:update:own` | `GroupService.requireManager` | `:94` |
| `/v1/groups` | DELETE | req | `group:delete:own` **hoặc** `group:delete` | `GroupService.delete` đòi role OWNER | `:103` |
| `/v1/groups/members` | DELETE | req | `group:update:own` | `GroupService.requireManager` | `:111` |
| `/v1/groups/{id}` (multipart/json) | PUT | req | `group:update:own` | `GroupService.requireManager` | `:125`, `:138` |
| `/v1/friendships/{id}` | GET | req | — | `friendshipService.areFriends(caller, id)` → 403 | `FriendshipController.java:39` |
| `/v1/friendships/send/{id}`, `/receive/{id}` | GET | req | — | `{id}` bị bỏ qua, dùng id trong token | `:53`, `:60` |
| `/v1/friendships` | POST | req | — | sender = token | `:67` |
| `/v1/friendships/accept`, `/reject` | POST | req | — | receiver = token | `:76`, `:85` |
| `/v1/friendships` | DELETE | req | — | một đầu = token | `:94` |
| `/v1/messages/{id}` | GET | req | — | `MessageAccessGuard.requireParticipant` (**NO_BYPASS**) | `MessageController.java:39` |
| `/v1/messages/status/{id}` | PATCH | req | — | `requireParticipant` **+ chỉ RECEIVER** (guard một mình cho phép cả người gửi) | `:46` |
| `/v1/messages/me/{id}` | GET | req | — | `{id}` bị bỏ qua, cặp hội thoại = (token, `?targetId`) | `:60` |
| `/v1/messages` (multipart/json) | POST | req | — | sender = token | `:68`, `:79` |
| `/v1/messages/delivered` | PATCH | req | — | **không dùng guard**: lọc `receiver = token` ngay trong truy vấn, id lạ bị bỏ qua thay vì 403 cả lô | `MessageService.markDelivered` |
| `/v1/messages/read` | PATCH | req | — | reader = token, đối phương = `?partnerId` | `MessageService.markConversationRead` |
| `/v1/messages/unread-count` | GET | req | — | chỉ đếm tin của token | `MessageService.countUnread` |
| socket `message delivered` / `message read` | — | req | — | danh tính từ `client.get("userId")`, **không từ payload** | `MessageSocketHandlers` |
| `/v1/notifications` | POST | req | — | actor = token | `NotificationController.java:31` |
| `/v1/notifications/{id}` | GET | req | — | **không có** (xem §18 `SEC-7`) | `:52` |
| `/v1/notifications/me/{id}` | GET | req | — | `{id}` bị bỏ qua | `:59` |
| `/v1/notifications/{id}` | PUT | req | — | **không có** (xem §18 `SEC-7`) | `:66` |
| `/v1/reports` | GET | req | `report:read` | — | `ReportController.java:32` |
| `/v1/reports` | POST | req | `report:create` | — | `:43` |
| `/v1/reports/{targetId}` | PUT | req | `report:resolve` | — | `:55` |
| `/v1/advertisements` | GET | — | — | — | `AdvertisementController.java:49` |
| `/v1/advertisements/{id}` | GET | — | — | — | `:55` |
| `/v1/advertisements` (multipart) | POST | req | `ads:create` | `AdvertiserService.requireApproved` | `:60` |
| `/v1/advertisements/{adsId}` (multipart/json) | PUT | req | `ads:update:own` | `AdvertisementAccessGuard.requireOwner` (bypass ADMIN) | `:77`, `:95` |
| `/v1/advertisements/{id}` | DELETE | req | `ads:delete:own` | `AdvertisementAccessGuard.requireOwner` | `:106` |
| `/v1/advertisers` | POST | req | `advertiser:register` | — | `AdvertiserController.java:31` |
| `/v1/advertisers/me` | GET | req | — | dùng id trong token | `:42` |
| `/v1/advertisers` | GET | req | `advertiser:read` | — | `:49` |
| `/v1/advertisers/{id}/status` | PUT | req | `advertiser:approve` | reviewer = token | `:56` |

---

## 6. Authorization

### 6.1 Authentication vs Authorization trong dự án này

| | Câu hỏi | Ai trả lời trong code này |
|---|---|---|
| **Authentication** | "Bạn là ai?" | `JwtAuthenticationFilter` giải mã token → `UserPrincipal(id, email, roles)`; `AuthInterceptor` quyết định request thiếu/hỏng token có được đi tiếp không |
| **Authorization** | "Bạn được phép làm gì?" | `RequirePermissionAspect` (hành động), `OwnershipGuard` (tài nguyên), `AdvertiserService.requireApproved` (tư cách), `FriendshipService.areFriends` (quan hệ), `PostVisibility` (dữ liệu đọc được) |

Ranh giới bị **mờ ở một chỗ**: `RequirePermissionAspect.java:20-22` ném `ForbiddenException` (403) khi
người gọi chưa xác thực — đó là câu trả lời của tầng authentication (401) nhưng phát ra từ tầng
authorization. Trên thực tế `@Auth` chạy trước nên nhánh này gần như không tới được; xem §18 `DES-3`.

### 6.2 Mô hình đang dùng: **RBAC + Ownership + Capability + Relationship**

Không phải RBAC thuần. Bốn cơ chế cùng tồn tại:

**(a) RBAC — role → permission, hard-code trong code**

`PermissionCatalog.java:64-69`:

```java
ROLE_PERMISSIONS = Map.of(
    RoleName.ADMIN,     List.of("*"),
    RoleName.MODERATOR, List.of("report:read", "report:resolve", "post:delete", "group:delete",
                                "account:ban", "ads:review", "advertiser:read"),
    RoleName.SUPPORT,   List.of("account:read", "report:read", "advertiser:read"));
```

`resolvePermissions(List<String> roles)` (`:72-91`):
1. Luôn nạp **toàn bộ 18 `BASELINE_PERMISSIONS`** (`:28-48`) cho mọi người gọi đã xác thực
   (15 mã gốc + `pet:create`, `pet:update:own`, `pet:delete:own`).
2. Với mỗi chuỗi role trong token: `RoleName.valueOf(role)`; **role lạ bị bỏ qua trong im lặng**
   (`:83-86`, có test `PermissionCatalogTest.role_la_khong_lam_mat_quyen_baseline`).
3. Cộng thêm permission của role đó.

`hasPermission(granted, required)` (`:94-99`): qua nếu có `*`, hoặc có đúng mã, hoặc có
`<resource>:*`. Hiện **không role nào** được cấp dạng `resource:*`, nhánh đó chỉ có test phủ.

**(b) Ownership** — `OwnershipGuard.check()` (`OwnershipGuard.java:45-70`). Xem §6.4.

**(c) Capability** — `AdvertiserService.requireApproved()` (`AdvertiserService.java:97-105`): tài
khoản phải có `advertiser_profiles` ở trạng thái `APPROVED`. Đây là thứ thay cho role `ADS` cũ, vì
role không mang được trạng thái duyệt (`RoleName.java:6-8`).

**(d) Relationship** — `FriendshipController.java:43` gọi `friendshipService.areFriends(callerId, id)`;
và `PostVisibility.VISIBLE_TO` nhánh D (bài `FRIEND` chỉ bạn bè `ACCEPTED` mới thấy).

**ACL:** `Not used in current implementation.` Không có bảng ACL, không có ACE per-object.

### 6.3 `@RequirePermission` hoạt động như thế nào

```text
File:    src/main/java/com/nguyenvu/lopet/security/RequirePermissionAspect.java
Package: com.nguyenvu.lopet.security
Class:   RequirePermissionAspect   (@Aspect, @Component)
Method:  check(RequirePermission)  — @Before("@annotation(requirePermission)")
```

**Purpose:** Chặn lời gọi tới handler nếu người gọi không có ít nhất một trong các mã quyền khai báo.

**Input:** chính instance annotation `RequirePermission` gắn trên method (Spring AOP bind theo tên
tham số `requirePermission`).

**Output:** `void` — hoặc ném `ForbiddenException` (403).

**Flow:**
1. `:20` `CurrentUser.optional()`; `null` → `ForbiddenException("Chưa xác thực")` → **403**.
2. `:25` `PermissionCatalog.resolvePermissions(principal.roles())` — tính từ **claim token**, không đọc DB.
3. `:26-27` `Arrays.stream(value()).anyMatch(...)` — nhiều mã hiểu theo nghĩa **HOẶC** (javadoc
   `RequirePermission.java:12-13`).
4. `:29-31` không qua → `ForbiddenException("Thiếu quyền: A hoặc B")` → **403**.

**Called by:** Spring AOP proxy, khi controller method mang `@RequirePermission`.

**Calls:** `CurrentUser.optional()`, `PermissionCatalog.resolvePermissions()`, `PermissionCatalog.hasPermission()`.

**Important behavior:**
- Annotation chỉ `@Target(ElementType.METHOD)` (`RequirePermission.java:19`) — không dùng được ở class.
- **Toàn bộ 15 permission baseline được cấp cho mọi tài khoản đã đăng nhập.** Vì vậy
  `@RequirePermission("post:create")`, `"comment:create"`, `"group:create"`, `"report:create"`,
  `"profile:update:own"`, `"advertiser:register"`, `"ads:create"`, `"*:*:own"`… **không lọc được ai
  cả** — chúng chỉ tương đương "đã đăng nhập". Giá trị bảo vệ thật của chúng là **tài liệu hoá ý định**
  và làm điểm móc nếu sau này baseline bị thu hẹp. Bảo vệ thật nằm ở tầng ownership/capability.
- Chỉ **11 permission staff** (`PermissionCatalog.java:48-59`) mới thực sự phân biệt được người gọi.

### 6.4 Ownership — `OwnershipGuard`

```text
File:    src/main/java/com/nguyenvu/lopet/security/authz/OwnershipGuard.java
Package: com.nguyenvu.lopet.security.authz
Class:   OwnershipGuard (final, private constructor — utility class)
Method:  <T> T check(Supplier<T> loader, Function<T, Collection<Integer>> ownersOf, Set<RoleName> bypassRoles)
```

**Flow (`:45-70`), thứ tự có ý nghĩa bảo mật:**
1. `:46-49` `CurrentUser.optional()`; `null` → `ForbiddenException("Chưa xác thực")` → 403.
2. `:51-56` **bypass được xét TRƯỚC khi gọi `loader`** — để staff kiểm duyệt vẫn xử lý được nội dung
   riêng tư mà bộ lọc quyền xem sẽ trả rỗng. Bypass → **`return null`**, tức là tài nguyên chưa từng
   được nạp.
3. `:57-60` `loader.get()`; `null` → `NotFoundException("Không tìm thấy tài nguyên")` → 404.
4. `:62-68` `ownersOf(resource)` (bỏ `null`) phải chứa `caller.id()`, nếu không →
   `ForbiddenException("Bạn không sở hữu tài nguyên này")` → 403.

Hai hằng bypass: `DEFAULT_BYPASS = Set.of(RoleName.ADMIN)` (`:35`) và `NO_BYPASS = Set.of()` (`:37`).

| Guard | Bypass | Lý do ghi trong javadoc |
|---|---|---|
| `PostAccessGuard.requireOwnerToEdit` (`:35`) | `NO_BYPASS` | "kiểm duyệt thì xoá, không viết hộ" |
| `PostAccessGuard.requireOwnerToDelete` (`:41`) | `DEFAULT_BYPASS` | ADMIN xoá được bài bất kỳ |
| `CommentAccessGuard.requireOwnerToDelete` (`:33`) | `DEFAULT_BYPASS` | kiểm duyệt bình luận |
| `MessageAccessGuard.requireParticipant` (`:29`) | `NO_BYPASS` | "nội dung tin nhắn riêng tư không phải thứ kiểm duyệt viên tự ý đọc" |
| `ProfileAccessGuard.requireOwner` (`:23`) | `NO_BYPASS` | "kiểm duyệt viên không sửa hộ hồ sơ" |
| `AdvertisementAccessGuard.requireOwner` (`:25`) | `DEFAULT_BYPASS` | — |

`PostAccessGuard` và `CommentAccessGuard` cố ý nạp bằng **query đã lọc quyền xem**
(`postRepository.findVisibleById(id, viewerId)`), để 403 và 404 không tiết lộ sự tồn tại của bài
PRIVATE (`PostAccessGuard.java:16-19`).

### 6.5 `hasRole` / `hasAuthority` / `@PreAuthorize` / `@Secured`

`Not used in current implementation.` Không có expression Spring Security nào trong `src/`.
Lý do chủ ý ghi ở `RequirePermission.java:15-17`. Hệ quả và cạm bẫy: xem §7.3.

---

## 7. Role and Authority Mapping

### 7.1 Các thành phần thật

| Khái niệm | Nơi định nghĩa |
|---|---|
| Role enum | `role/entity/RoleName.java:10-14` — `ADMIN`, `MODERATOR`, `SUPPORT`. **Không có `USER`, không có `ADS`** |
| Role entity | `role/entity/Role.java` — bảng `roles`, cột `name` là `enum('ADMIN','MODERATOR','SUPPORT')` (`:45-47`) |
| Permission entity | `role/entity/Permission.java` — bảng `permissions` (`code` unique, `resource`, `action`, `scope`, `description`) |
| Scope enum | `role/entity/PermissionScope.java` — `ANY` / `OWN` |
| User ↔ Role | `account/entity/AccountRole.java` — bảng `account_role`, khoá chính kép `(account_id, role_id)` qua `@IdClass(AccountRoleId)`; thêm `granted_by`, `granted_at` |
| Role ↔ Permission | `Role.permissions` — `@ManyToMany` `@JoinTable(name="role_permission")` (`Role.java:57-62`) |
| Nguồn quyết định runtime | `security/authz/PermissionCatalog.java` — **hard-code trong code, không đọc DB** |
| Seeder | `bootstrap/AuthorizationSeeder.java:38-73` — đồng bộ 3 bảng theo `PermissionCatalog`, idempotent |

**Vì sao không có role `USER`:** `RoleName.java:5-8` — "user thường" = tài khoản đã xác thực, được
cấp `BASELINE_PERMISSIONS` và **không có bản ghi nào trong `account_role`**.

### 7.2 Sơ đồ thực tế

Sơ đồ mẫu trong đề bài (`User → Role → Permission → GrantedAuthority → Spring Security Authorization`)
**không đúng với code này**. Luồng thật rẽ đôi:

```text
                      accounts ──< account_role >── roles ──< role_permission >── permissions
                          │                            │                              │
                    (chỉ đọc lúc LOGIN)                │                     (chỉ để truy vấn/hiển thị;
                          │                            │                      seed từ PermissionCatalog)
                          ▼                            │
        AuthService.rolesOf(account)  ─────────────────┘
                          │  List<String> tên role
                          ▼
        JwtService.sign()  → claim "roles" trong JWT
                          │
        ══════════════════╪══════════ ranh giới request ══════════════════════
                          ▼
        JwtService.parse() → UserPrincipal.roles()
                          │
             ┌────────────┴──────────────────────────────┐
             ▼                                           ▼
  PermissionCatalog.resolvePermissions()      SimpleGrantedAuthority("ROLE_" + role)
  (baseline + ROLE_PERMISSIONS hard-code)      → gắn vào UsernamePasswordAuthenticationToken
             │                                           │
             ▼                                           ▼
  RequirePermissionAspect / OwnershipGuard      ❌ KHÔNG AI ĐỌC  (permitAll, không method security)
             │
             ▼
      403 hoặc cho đi tiếp
```

Ba hệ quả đọc được thẳng từ code:

1. **Bảng `role_permission` không tham gia quyết định runtime.** `PermissionRepository` /
   `RoleRepository` chỉ được dùng bởi `AuthorizationSeeder` và `RoleController.getList()`. Không có
   truy vấn permission nào trong đường đi của một request nghiệp vụ. Javadoc `PermissionCatalog.java:14-17`
   và `Permission.java:21-23` xác nhận đây là chủ ý.
2. **Role chỉ được đọc từ DB đúng một lần: lúc login** (`AuthService.java:46`, `:133-135`). Sau đó mọi
   thứ dựa vào claim trong token.
3. **`GrantedAuthority` là code chết.**

### 7.3 ⚠️ Potential Issue — `hasRole("ADMIN")` vs `hasAuthority("ADMIN")`

Giải thích **dựa trên cách project này tạo `GrantedAuthority`**:

`JwtAuthenticationFilter.java:53-55`

```java
List<SimpleGrantedAuthority> authorities = principal.roles().stream()
        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
        .toList();
```

Với token có `roles: ["ADMIN"]`, authority thật trong `SecurityContext` là chuỗi **`"ROLE_ADMIN"`**.

| Biểu thức | Spring Security so sánh với | Kết quả **nếu** dự án bật method security |
|---|---|---|
| `hasRole("ADMIN")` | tự thêm tiền tố → `"ROLE_ADMIN"` | ✅ khớp |
| `hasRole("ROLE_ADMIN")` | → `"ROLE_ROLE_ADMIN"` | ❌ không khớp |
| `hasAuthority("ADMIN")` | so nguyên văn `"ADMIN"` | ❌ **không khớp** |
| `hasAuthority("ROLE_ADMIN")` | so nguyên văn `"ROLE_ADMIN"` | ✅ khớp |

**⚠️ Potential Issue — `AUTHZ-1`:** hai dòng `:53-55` sinh ra một tập authority mà **hiện không có
đoạn code nào đọc** (`permitAll` ở `SecurityConfig.java:36` + không có `@EnableMethodSecurity`). Đây
là bẫy ba mặt:

- Người đọc mới thấy `ROLE_` prefix sẽ tưởng phân quyền chạy bằng Spring Security, rồi đi tìm
  `authorizeHttpRequests` — không có gì ở đó.
- Ai thêm `@PreAuthorize("hasAuthority('ADMIN')")` sẽ bị **từ chối im lặng** (403) dù đúng là ADMIN,
  vì authority thật là `ROLE_ADMIN`.
- Ai thêm `@PreAuthorize("hasRole('ADMIN')")` sẽ **chạy được** — tạo ra hệ thống phân quyền thứ hai
  song song với `PermissionCatalog`, đúng thứ mà javadoc `SecurityConfig.java:16-21` muốn tránh.

**⚠️ Potential Issue — `AUTHZ-2`:** `RoleName` không có `USER`, nhưng token cũ (từ backend TypeScript)
có thể mang `roles: ["USER"]` hoặc `["ADS"]`. `PermissionCatalog.java:83-86` bỏ qua trong im lặng —
đúng ý đồ. Nhưng `JwtAuthenticationFilter.java:54` vẫn tạo `SimpleGrantedAuthority("ROLE_USER")`, một
authority không tương ứng với bất kỳ `RoleName` nào. Nếu sau này ai đó viết `hasRole('USER')` thì
biểu thức đó **sẽ pass**, dù `RoleName.USER` không tồn tại.
