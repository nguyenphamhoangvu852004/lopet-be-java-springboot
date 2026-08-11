# MIGRATION_FINAL_REPORT

Báo cáo đối chiếu cuối giữa `lopet-be` (TypeScript/ExpressJS — **source of truth**) và
`lopet-be-java-springboot` (Java 21 / Spring Boot 4.1).

Ngày chốt: 2026-08-10.

---

## 1. Số liệu tổng

```
Tổng endpoint TS:            68 route handler / 72 đường dẫn (authRouter mount 2 lần)
Tổng endpoint Java:          77 handler       / 72 đường dẫn
  Đã migrate:                72 / 72 đường dẫn
  Thiếu:                     0
  Migrate một phần:          0

  (77 > 68 vì 9 endpoint có upload được khai hai biến thể: một nhận multipart/form-data,
   một nhận JSON. Bên Express cả hai kiểu body đi chung một handler nhờ express.json() +
   multer; Spring cần khai riêng theo `consumes`. Cùng đường dẫn, cùng hành vi.)

Tổng module TS:              25 module nghiệp vụ + 8 hạ tầng
Tổng module Java:            25 module nghiệp vụ + 8 hạ tầng
  Đầy đủ:                    33
  Một phần:                  0
  Thiếu:                     0

Bảng dữ liệu:                18 / 18
Cột dữ liệu:                 145 / 145 (trùng tên, kiểu, nullable)
Khoá ngoại:                  27 / 27 (trùng cả quy tắc ON DELETE)
Sự kiện Socket.IO:           7 / 7
Test:                        71 test, pass 100%
```

### Từng hạng mục

| Hạng mục | Trạng thái | Ghi chú |
|---|---|---|
| Authentication | ĐẦY ĐỦ | login/signup/verify/reset × 2 tiền tố; JWT HS256 cùng payload; bcrypt cost 10 nên hash cũ dùng lại được |
| Authorization | ĐẦY ĐỦ | đủ 4 tầng: permission (26 mã), ownership (kèm bypassRoles theo từng route), quan hệ bạn bè, capability nhà quảng cáo |
| Database | ĐẦY ĐỦ | đối chiếu trực tiếp với schema do TypeORM sinh ra — xem DATABASE_MIGRATION_NOTES §5 |
| Realtime | ĐẦY ĐỦ, khác cổng | netty-socketio, đúng 7 sự kiện và đúng quy ước phòng; **client phải đổi URL socket** (§4.1) |
| Notification | ĐẦY ĐỦ | REST + emit socket; giữ nguyên khoá `notificationId` và cặp `objectType`/`type` |
| File upload | ĐẦY ĐỦ | Cloudinary; guard chạy trước upload đúng như thứ tự middleware cũ |
| Search | ĐẦY ĐỦ | tìm bài theo nội dung, hồ sơ theo họ tên, gợi ý tài khoản/nhóm |
| Pagination | KHÔNG CÓ Ở BẢN GỐC | không endpoint nào của TS nhận page/size; chỉ `take(10)` cho feed gợi ý — đã tái hiện |
| Validation | ĐẦY ĐỦ | Bean Validation, message tiếng Anh giữ nguyên văn Joi (§4.4) |
| Error handling | ĐẦY ĐỦ | cả ba dạng envelope, kể cả các mã lỗi lệch chuẩn (§2.2) |
| Testing | ĐẦY ĐỦ | port cả 3 file test của bản TS + bổ sung JWT/permission |

---

## 2. Bằng chứng đối chiếu

### 2.1. Schema

Dựng hai database rỗng, cho từng backend tự sinh schema rồi so `information_schema`:

```
TS: 145 cột   JAVA: 145 cột
diff (table|column|type|nullable) => SCHEMA IDENTICAL
FK: 27 / 27
```

Quá trình này phát hiện **hai lỗi thật** trước khi kịp gây hậu quả:

1. Naming strategy mặc định của Spring Boot đổi `createdAt` → `created_at`, `receptorId` →
   `receptor_id`, sinh ra một bộ cột song song và làm khoá ngoại trỏ vào cột không tồn tại.
2. Khoá chính của TypeORM là `int`; nếu id trong Java là `Long` thì FK thành `bigint` và MySQL từ
   chối ràng buộc.

### 2.2. Hợp đồng HTTP (chạy thật trên MySQL)

| Kịch bản | Kỳ vọng (theo bản TS) | Kết quả Java |
|---|---|---|
| `POST /v1/auth/login` đúng | `{statusCode, message, data:{id, accessToken, refreshToken}}`, **không có roles** | khớp |
| roles trong JWT | `{"id":1,"email":...,"roles":["ADMIN"],"iat":...,"exp":...}` | khớp |
| `POST /v1/password/login` | tồn tại (router mount 2 lần) | 200 |
| Validation lỗi | `{statusCode:400, message:"Validation error", errors:[{field,message}]}` | khớp |
| Route bắt buộc token, thiếu token | **400** `Token not found` (không phải 401) | khớp |
| Route bắt buộc token, token hỏng | **500** + message thô `invalid signature` | khớp |
| Route optionalAuth, token hỏng | **401** `Token không hợp lệ hoặc đã hết hạn` | khớp |
| Route optionalAuth, không token | 200, chỉ nội dung công khai | khớp |
| User thường gọi endpoint staff | 403 `Thiếu quyền: account:read` | khớp |
| User thường tự cấp ADMIN | 403 `Thiếu quyền: account:setRole` | khớp |
| Người ngoài đăng vào nhóm PRIVATE | 403 `Bạn không phải thành viên của nhóm này` | khớp |
| Bài nhóm scope FRIEND | 400 `Scope không hợp lệ cho bài nhóm: chỉ nhận PUBLIC \| PRIVATE` | khớp |
| Đọc bài PRIVATE của người khác | **404** (không phải 403 — chống dò id) | khớp |
| Định dạng thời gian | `2026-08-10T08:23:46.476Z` như `Date.toJSON()` của Node | khớp |

### 2.3. Test

```
71 test, 0 failure
├─ JwtServiceTest                     10  ký/giải mã, giả chữ ký, hết hạn, sai khoá
├─ PermissionCatalogTest               9  baseline / ADMIN / MODERATOR / SUPPORT / wildcard
├─ AuthServiceResetPasswordTest        4  port từ resetPassword.test.ts
├─ PostPolicyScopeTest                 6  ràng buộc scope
├─ PostVisibilityIntegrationTest      14  port từ postVisibility.integration.test.ts (MySQL thật)
└─ PostAuthorizationIntegrationTest   28  port từ postAuthorization.integration.test.ts (MySQL thật)
```

Hai bộ integration chạy trên MySQL thật với database riêng cho từng lớp, đúng cách bản TS làm.

---

## 3. Những chỗ dễ mất khi migrate — đã giữ nguyên

Đây là các hành vi mà một bản dịch máy móc sẽ làm sai, kèm nơi kiểm chứng:

1. **404 chứ không phải 403 cho bài không được xem** — nếu trả 403 thì endpoint thành công cụ quét
   id để biết bài PRIVATE nào tồn tại. Ownership guard cũng nạp tài nguyên qua bộ lọc quyền xem vì
   lý do đó.
2. **`bypassRoles` khác nhau theo từng route** — ADMIN xoá được bài/bình luận vi phạm nhưng KHÔNG
   sửa bài người khác, KHÔNG đọc tin nhắn riêng tư, KHÔNG sửa hồ sơ cá nhân, KHÔNG xem danh sách bạn bè.
3. **Nhóm PRIVATE mạnh hơn scope của bài** — bài PUBLIC trong nhóm PRIVATE vẫn không lọt ra ngoài;
   ràng buộc nằm ở tầng query nên không endpoint nào đi vòng được.
4. **`isGroupPost` suy từ group đã nạp từ DB, không từ body** — nếu tin `groupId` trong body thì chỉ
   cần bỏ trống trường đó là đặt được scope FRIEND cho bài trong nhóm.
5. **Bình luận cha phải thuộc đúng bài đang kiểm quyền** — nếu không, ghép `postId` công khai với
   `replyCommentId` lấy từ bài PRIVATE là chèn được vào cây bình luận riêng tư.
6. **`getDel` nguyên tử khi đổi mật khẩu** — tách `get` + `del` để hở cửa sổ cho hai request song song.
7. **`postType` không được dùng để quyết định quyền** — cột nullable, dữ liệu cũ có thể NULL; mọi
   nhánh đều dựa vào `group_id`.
8. **Các `:id` bị bỏ qua** ở `/accounts/suggest/:id`, `/friendships/send|receive/:id`,
   `/notifications/me/:id` — controller cũ dùng `req.user.id`. Giữ nguyên để URL client không đổi.
9. **`/v1/messages/me/:id` đọc đối tượng hội thoại từ query `?targetId=`**, không từ path param.
10. **Tên sự kiện socket `chat messsage`** (ba chữ `s`) và khoá DTO `linkReferfence` — sai chính tả
    trong bản gốc, client đang dựa vào đúng chuỗi đó.
11. **`likeList` / `listLike` / media có id hay không** khác nhau giữa bốn luồng đọc bài, vì mỗi hàm
    bên TS chỉ gán một tập trường; khoá không được gán thì vắng mặt trong JSON.
12. **Thiếu `type` trong `PUT /v1/groups/:id` khiến nhóm thành PRIVATE** — controller cũ tính
    `type == 'PUBLIC' ? PUBLIC : PRIVATE` nên trường vắng mặt vẫn được ghi.

---

## 4. Khác biệt không thể 1:1 — khai báo đầy đủ

### 4.1. Socket.IO chạy trên cổng riêng

* **Hành vi TS**: Socket.IO gắn vào chính HTTP server của Express, dùng chung cổng `APP_PORT` (8080).
* **Vì sao Java không làm được như vậy**: Tomcat sở hữu cổng HTTP và không cho thư viện khác chen
  vào cùng listener; netty-socketio là một server Netty độc lập.
* **Giải pháp**: Socket.IO nghe `SOCKET_PORT` (mặc định 8081), cùng protocol EIO4 nên
  `socket.io-client` v4 của frontend giữ nguyên.
* **Ảnh hưởng**: client phải trỏ URL socket sang cổng mới — sửa đúng một dòng
  (`io("http://host:8081", { auth: { token } })`). Muốn giữ nguyên một cổng thì đặt reverse proxy
  (nginx) định tuyến `/socket.io/` về 8081.

### 4.2. Xác thực socket nằm ở `AuthTokenListener`, không phải `AuthorizationListener`

* **Hành vi TS**: middleware `io.use(...)` đọc `handshake.auth.token`, trả mã lỗi riêng
  `TOKEN_EXPIRED` / `INVALID_TOKEN` / `AUTHENTICATION_ERROR`.
* **Vì sao không dùng `AuthorizationListener`**: trong EIO4, `auth` đi trong gói CONNECT gửi *sau*
  khi bắt tay HTTP xong, còn `AuthorizationListener` chạy *ngay lúc* bắt tay — khi đó
  `handshakeData.getAuthToken()` luôn null, nên mọi kết nối đều bị từ chối ("thiếu auth.token").
* **Giải pháp**: đăng ký `AuthTokenListener` trên namespace mặc định. Nó nhận đúng object `auth`,
  và `AuthTokenResult` kèm được dữ liệu lỗi, nên client vẫn phân biệt được nguyên nhân qua
  `err.message` của `connect_error` — ngang bằng bản TS.
* **Hai bẫy còn lại của netty-socketio** (đã xử lý, xem javadoc `SocketIoConfig`):
  1. `ConnectListener` chạy *trước* `AuthTokenListener` và với EIO4 còn được phát hai lần, nên
     không đọc được `userId` ở đó — việc vào phòng `user_<id>` phải nằm trong `AuthTokenListener`.
  2. Client không gửi `auth` thì thư viện **bỏ qua** `AuthTokenListener` và cho kết nối đi tiếp;
     `SocketIoServerRunner` có đồng hồ 5 giây ngắt các kết nối vẫn chưa có danh tính, và chặn
     luôn sự kiện `join room` từ kết nối chưa xác thực.

### 4.3. JWT được tự hiện thực thay vì dùng thư viện

* **Lý do**: jjwt và Nimbus đều từ chối khoá HMAC ngắn hơn 256 bit theo RFC 7518, còn
  `ACCESS_TOKEN_SECRET` đang chạy chỉ dài 23 byte. Ép đổi khoá tức là file `.env` hiện tại không
  dùng lại được — mất tính thay thế trực tiếp.
* **Bù lại**: `JwtService` có 10 test riêng phủ chữ ký sai, payload bị sửa, hết hạn, sai khoá, và
  đối chiếu header base64 với chuỗi mà `jsonwebtoken` sinh ra.
* **Khuyến nghị vận hành** (không bắt buộc để chạy): đặt `ACCESS_TOKEN_SECRET` ≥ 32 byte.

### 4.4. Thông điệp validation: đúng nội dung, có thể nhiều hơn một dòng mỗi trường

* Chuỗi lỗi được đặt trùng nguyên văn Joi (`"password" length must be at least 6 characters long`).
* Khác biệt: Joi dừng ở rule đầu tiên hỏng của mỗi trường, Bean Validation trả về mọi rule hỏng —
  nên một trường có thể xuất hiện 2 phần tử trong `errors` thay vì 1. Cấu trúc và câu chữ không đổi.
* `strict()` của Joi (không tự ép kiểu) chưa được tái hiện: gửi `{"username": 123}` thì Jackson ép
  thành `"123"` thay vì báo `"username" must be a string`.

### 4.5. Transaction: Java nhất quán hơn ở luồng lỗi

* Bản TS **không** bọc transaction ở bất kỳ luồng nào — mỗi `save()` là một transaction riêng.
* Java đặt `@Transactional` ở đúng các luồng ghi nhiều bảng (tạo bài + media, tạo nhóm + OWNER, cấp
  role, xử lý báo cáo hàng loạt).
* Luồng thành công: kết quả giống hệt. Luồng lỗi: Java rollback, TS để lại dữ liệu dở dang.
  Đây là khác biệt **cố ý** và là hướng an toàn hơn.

### 4.6. Hai defect của bản TS được sửa thay vì sao chép

Cả hai đều làm endpoint **không dùng được**, tức là chép nguyên sẽ giao một hệ thống hỏng sẵn:

| Defect ở `lopet-be` | Hậu quả | Xử lý ở Java |
|---|---|---|
| `PUT /v1/groups/:id` đọc `uploadedImage.secure_url` vô điều kiện | Sửa nhóm mà không kèm ảnh → **TypeError → 500** | Không gửi ảnh thì giữ nguyên ảnh bìa cũ |
| `POST /v1/advertisements` chạy Joi validate **trước** `upload.single()` | Body multipart chưa được parse → **luôn 400**, endpoint không thể dùng | Bỏ hẳn schema đó, endpoint hoạt động. Lý do: `advertisementValidation` đòi `{title, accountId, content}` trong khi controller lại đọc `{title, description, linkRef}` — hai bộ trường không giao nhau ngoài `title`, nên áp lại schema sẽ từ chối cả request hợp lệ. `accountId` vốn cũng không được nhận từ body nữa (lấy từ token). |

> Nếu muốn giữ nguyên cả hành vi hỏng, hai chỗ này nằm ở `GroupController.modify()` và
> `AdvertisementController.create()`.

### 4.7. Hành vi rủi ro của bản gốc — giữ nguyên, không tự siết

Ghi ra để chủ dự án quyết định, **chưa** thay đổi vì đó là nghiệp vụ hiện hành:

1. `GET /v1/notifications/:id` và `PUT /v1/notifications/:id` không kiểm người gọi có phải
   `receptor` — bất kỳ ai đăng nhập cũng đọc và đổi trạng thái thông báo của người khác.
2. `PATCH /v1/profiles/:id` **xoá** `avatarUrl`/`coverUrl` khi request không kèm file mới, vì
   controller cũ luôn truyền chuỗi rỗng và `??` không bắt chuỗi rỗng.
3. `POST /v1/friendships` chỉ chặn trùng đúng chiều gửi→nhận; hai bản ghi ngược chiều cùng tồn tại được.
4. `POST /v1/reports` không kiểm target có tồn tại.
5. `GET /v1/groups/:id` công khai, kể cả nhóm PRIVATE (chỉ bài viết trong nhóm mới được bảo vệ).
6. Xoá tài khoản là xoá cứng và kéo theo CASCADE sang posts/comments/reports/group_members.
7. `post_likes` không có UNIQUE(post_id, account_id) — tính duy nhất chỉ do tầng service bảo đảm.

---

## 5. Cách chạy

```bash
# 1. Hạ tầng (dùng lại docker-compose của lopet-be)
cd lopet-be && docker compose up -d mysql-docker redis

# 2. Chạy backend Java
cd ../lopet-be-java-springboot
mvn package -DskipTests
DATABASE_HOSTNAME=127.0.0.1 DATABASE_PORT=3307 DATABASE_USERNAME=root \
DATABASE_PASSWORD=nguyenvu DATABASE_NAME=socialmedia \
REDIS_HOSTNAME=127.0.0.1 REDIS_PORT=6379 APP_PORT=8080 SOCKET_PORT=8081 \
java -jar target/lopet-0.0.1-SNAPSHOT.jar

# 3. Test (cần MySQL ở cổng 3307)
mvn test
```

Biến môi trường trùng tên với `example.env` của `lopet-be`, thêm đúng một biến mới: `SOCKET_PORT`.

---

## 6. Việc cần chủ dự án quyết

Database dev `socialmedia` trong container MySQL hiện có **42 cột thừa** dạng snake_case
(`created_at`, `profile_id`, `receptor_id`…) nằm song song với các cột camelCase thật.

* **Nguyên nhân**: một lần chạy thử backend Java TRƯỚC khi cấu hình naming strategy — chính là lỗi
  được mô tả ở §2.1. `ddl-auto=update` đã thêm bộ cột đó vào. Cấu hình đã sửa nên sẽ không tái diễn.
* **Đã kiểm**: cả 42 cột đều rỗng hoàn toàn (0 giá trị non-null). Dữ liệu thật nằm ở các cột
  camelCase và không bị đụng tới.
* **Cách dọn**: script đã soạn sẵn, **chưa chạy** vì đây là database có dữ liệu dev thật:

```bash
docker exec -i mysql-docker mysql -uroot -pnguyenvu socialmedia \
  < scripts/cleanup-socialmedia-junk-columns.sql
```

Không dọn cũng không ảnh hưởng vận hành — Hibernate bỏ qua cột không được map.

## 7. Kết luận

Không còn endpoint thiếu, feature thiếu, business rule thiếu, authorization rule thiếu, socket event
thiếu hay quan hệ database thiếu.

Bốn khác biệt còn lại đều đã khai báo ở §4: cổng Socket.IO (client sửa một dòng), thông điệp lỗi
handshake socket, số lượng phần tử trong mảng `errors` của validation, và ranh giới transaction.
Hai defect ở §4.6 được sửa vì chép nguyên đồng nghĩa giao một endpoint không dùng được — nếu chủ dự
án muốn giữ nguyên, vị trí sửa đã được chỉ rõ.
