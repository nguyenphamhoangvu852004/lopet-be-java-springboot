# GROUP_MANAGEMENT

Cơ chế nhóm: `PUBLIC`/`PRIVATE`, tự tham gia, yêu cầu chờ duyệt, lời mời.

Trước bản này `groups.type` đã có sẵn hai giá trị `PUBLIC`/`PRIVATE`, nhưng nó chỉ ảnh hưởng tới
**quyền đọc bài** (nhánh B của `PostVisibility`) và **quyền đăng bài vào nhóm PRIVATE**. Vòng đời
thành viên quanh nó thì không tồn tại:

- không có endpoint nào để **tự tham gia** — đường duy nhất vào nhóm là quản trị nhóm `INSERT` thẳng
  một hàng, tức là người "được mời" thành thành viên ngay mà **không được hỏi**;
- không có endpoint nào để **rời nhóm** — chỉ quản trị nhóm xoá được người khác;
- `group_members` **không có cột trạng thái**, nên một yêu cầu chờ duyệt hay một lời mời chưa trả lời
  là thứ không diễn tả được;
- `GET /v1/groups/{id}` không xác thực và trả nguyên `members[]`, nên handle + ảnh của từng thú cưng
  trong **mọi nhóm kín** đọc được chỉ bằng cách đoán id nhóm.

---

## 1. Hai loại nhóm

| | đọc / thích / bình luận bài | đăng bài | tham gia | rời nhóm |
|---|---|---|---|---|
| **PUBLIC** | mọi người, kể cả khách | thành viên ACTIVE | tự do, vào ngay | tự do |
| **PRIVATE** | chỉ thành viên ACTIVE | thành viên ACTIVE | gửi yêu cầu → OWNER/ADMIN duyệt | tự do |

Không phụ thuộc loại nhóm: **mọi thành viên ACTIVE đều mời được** người khác, và **chính pet được
mời** chấp nhận hoặc từ chối.

### Đăng bài đòi tham gia ở CẢ HAI loại nhóm

Trước đây nhóm PUBLIC cho bất kỳ ai đã đăng nhập đăng bài vào mà không cần tham gia — nghĩa là nút
"tham gia nhóm" chẳng thay đổi điều gì. Nay `PostPolicy.resolveGroupForPost` đòi thành viên ACTIVE ở
cả hai loại. Đọc/thích/bình luận bài của nhóm PUBLIC thì vẫn mở cho mọi người kể cả khách — chỉ hành
động **đăng** mới là hành động của thành viên.

### PRIVATE che gì

Bài viết: `PostVisibility` lo, không endpoint nào đi vòng được.
Danh sách thành viên: `GroupService.getById` rút `members` thành rỗng và bật cờ `restricted`.
Metadata (tên, bio, ảnh bìa, `totalMembers`): **vẫn công khai** — chọn như vậy chứ không trả 404 để
nhóm kín còn **tìm được** mà xin vào; che hẳn thì không có đường nào gửi yêu cầu.

---

## 2. Mô hình dữ liệu

`group_members` giữ nguyên khoá chính ghép `(group_id, pet_id)` và nhận thêm hai cột:

```
status      enum('PENDING','ACTIVE') NOT NULL DEFAULT 'ACTIVE'
invited_by  int NULL                 -- KHÔNG có khoá ngoại, xem §2.2
```

### 2.1 `PENDING` mang hai nghĩa, phân biệt bằng `invited_by`

```
status = ACTIVE                       -> thành viên thật
status = PENDING, invited_by IS NULL  -> pet TỰ xin vào nhóm PRIVATE   -> quản trị nhóm duyệt
status = PENDING, invited_by NOT NULL -> được thành viên mời           -> CHÍNH PET ĐƯỢC MỜI duyệt
```

Chọn một cột thay vì thêm giá trị enum thứ ba (`INVITED`) vì "ai được quyền trả lời hàng này" là
thông tin **suy ra được từ dữ liệu**, không cần thêm cờ. Hệ quả trực tiếp: `reviewJoinRequest` lọc
`invited_by is null` nên quản trị nhóm **không** tự duyệt được một lời mời mà người được mời chưa
đồng ý, và `respondToInvite` lọc `invited_by is not null` nên không ai tự chấp nhận hộ một yêu cầu.

Không có `REJECTED`: từ chối thì **xoá hàng**, đúng như rời nhóm. Nhờ vậy pet bị từ chối xin lại
được, pet từ chối lời mời được mời lại, và mọi truy vấn chỉ phải phân biệt hai trạng thái.

### 2.2 Vì sao `invited_by` không có khoá ngoại

Cùng lý do như `notifications.objectId`. Hai lựa chọn còn lại đều sai:

- `ON DELETE SET NULL` — xoá pet đã mời sẽ biến một **lời mời** thành một **yêu cầu xin vào**, và
  quản trị nhóm duyệt được một hàng mà người bị mời chưa hề đồng ý. Đúng cái lệch nghĩa mà §2.1 dựa
  vào để phân quyền.
- `ON DELETE CASCADE` — xoá pet đã mời sẽ xoá cả những hàng **đã thành ACTIVE**, tức là đá một thành
  viên thật ra khỏi nhóm chỉ vì người từng mời họ bị xoá.

Đổi lại, giá trị có thể trỏ tới một pet không còn tồn tại; `listMyInvites` trả object rỗng cho
`invitedBy` trong trường hợp đó.

### 2.3 `joined_at` vs `createdAt`

`joined_at` là lúc tư cách thành viên **bắt đầu thật** — hàng PENDING được đóng dấu lại khi được
duyệt hoặc được chấp nhận. Lúc gửi yêu cầu / lúc được mời thì đọc `createdAt` của `BaseEntity`.

---

## 3. Thành viên là THÚ CƯNG

Khoá chính là `(group_id, pet_id)`, nên mọi endpoint ghi lấy chủ thể từ `PetContext.require()` và
**không bao giờ** từ id tài khoản trong token. Hệ quả có chủ đích: một người có hai thú cưng, đưa con
A làm chủ nhóm, thì khi đang thao tác nhân danh con B họ **không** quản trị được nhóm đó.

Ngoại lệ là hai route liệt kê theo tài khoản (`/owned/:id`, `/joined/:id`) — chúng gộp mọi thú cưng
của tài khoản, vì đó là câu hỏi mà màn hình "nhóm của tôi" thật sự đang hỏi.

Người nhận **thông báo** thì vẫn là **tài khoản** (`pet.getAccount().getId()`): đồ thị notification ở
lại phạm vi tài khoản vĩnh viễn.

---

## 4. Ba mức phân quyền trong nhóm

```
requireActiveMember  bất kỳ thành viên ACTIVE  -> MỜI người khác
requireManager       OWNER hoặc ADMIN          -> duyệt yêu cầu, xoá thành viên, sửa thông tin nhóm
đúng OWNER                                     -> xoá nhóm
```

**Mời đã nới từ quản trị nhóm xuống mọi thành viên** — an toàn vì lời mời chỉ là một hàng PENDING,
không cấp quyền đọc gì cho tới khi người được mời đồng ý.

Chủ nhóm **không rời nhóm được** (400): cùng lý do `removeMember` từ chối xoá OWNER — nhóm sẽ còn lại
mà không có ai đủ quyền xoá hoặc sửa nó. Chủ nhóm muốn dứt thì xoá nhóm.

Ở tầng HTTP: route tự phục vụ chỉ cần `@Auth @RequirePet`, route quản trị dùng lại mã
`group:update:own` có sẵn. **`PermissionCatalog` không phải đổi** — không mã quyền toàn cục nào diễn
tả được "là thành viên của đúng nhóm này", nên luật thật nằm ở service.

---

## 5. Endpoint

### Tự phục vụ — `@Auth @RequirePet`

| Method | Path | Ghi chú |
|---|---|---|
| POST | `/v1/groups/{id}/join` | PUBLIC → ACTIVE ngay; PRIVATE → PENDING. Đọc `status` trong phản hồi. 201 |
| DELETE | `/v1/groups/{id}/join` | huỷ yêu cầu do chính pet gửi |
| DELETE | `/v1/groups/{id}/leave` | OWNER → 400 |
| POST | `/v1/groups/invites` | body `{groupId, invitee}` — **nay tạo lời mời PENDING**, không thêm thẳng. 201 |
| GET | `/v1/groups/invites/mine` | hộp thư lời mời của pet đang thao tác |
| POST | `/v1/groups/invites/accept` \| `/reject` | body `{groupId}` |

### Quản trị nhóm — `@Auth @RequirePermission("group:update:own") @RequirePet`

| Method | Path | Ghi chú |
|---|---|---|
| GET | `/v1/groups/{id}/requests` | chỉ yêu cầu tự gửi, không lẫn lời mời |
| POST | `/v1/groups/requests/approve` \| `/reject` | body `{groupId, petId}` |

### Đọc

`GET /v1/groups/{id}` nay có `@Auth(required = false)` + `PetContext.optional()`. Không gắn
`@RequirePet`: bắt buộc header `X-Pet-Id` ở một route đọc công khai sẽ chặn luôn khách.

```json
{
  "id": 12, "name": "...", "type": "PRIVATE", "bio": "...", "coverUrl": "...",
  "members": [],
  "totalMembers": 7,
  "restricted": true,
  "viewerStatus": "PENDING_REQUEST"
}
```

`totalMembers` **luôn** đếm đúng số thành viên ACTIVE, kể cả khi `members` bị che. Client phải đọc cờ
`restricted` chứ không suy ra từ `members.isEmpty()` — một nhóm PUBLIC rỗng cũng cho danh sách rỗng.

`viewerStatus` ∈ `NONE | PENDING_REQUEST | PENDING_INVITE | MEMBER`, là thứ quyết định nút hiển thị
là *Tham gia* / *Đã gửi yêu cầu* / *Rời nhóm*. Khách chưa đăng nhập và người chưa chọn pet đều nhận
`NONE` — cả ba trường hợp đều "chưa dính gì tới nhóm này".

Các route liệt kê (`/suggest`, `/owned/:id`, `/joined/:id`, `/joined/pets/:id`) giữ nguyên: không xác
thực, và chỉ đếm/liệt kê hàng ACTIVE.

---

## 6. `status = ACTIVE` là điều kiện KHÔNG được bỏ sót

Đây là phần dễ sai nhất của bản này. Vì hàng PENDING sinh ra từ **hành động tự nguyện của chính người
ngoài** (bấm xin vào nhóm PRIVATE), một truy vấn quên lọc `status` sẽ để người ngoài tự mở cửa vào
nhóm kín.

`GroupMemberRepository` vì vậy tách làm hai họ, ghi rõ trong javadoc của interface:

- `findActive*` / `count*` — chỉ hàng ACTIVE. **Mọi câu hỏi phân quyền và mọi danh sách hiển thị dùng
  nhóm này.**
- `findByGroupIdAndPetId` và `findPending*` — đọc được cả hàng PENDING, chỉ dành cho luồng xin
  vào / mời / duyệt, nơi sự tồn tại của hàng chờ mới là thông tin cần biết.

Các chỗ đã được siết:

| Nơi | Trả lời câu hỏi |
|---|---|
| `PostVisibility.VISIBLE_TO` nhánh D | ai đọc được bài trong nhóm — **chỗ duy nhất** thực thi tính riêng tư của nhóm ở đường đọc |
| `PostPolicy.resolveGroupForPost` | ai đăng được bài vào nhóm |
| `countManagers` | ai duyệt / kick / sửa nhóm được |
| `countActiveByGroupIdAndPetIdAndRole` | ai xoá nhóm được |
| `findActiveByPetId`, `findByOwnerAccountId(AndRole)` | các route "nhóm của tôi" |
| `GroupService.GroupMapper.activeMembersOf` | `members[]`, `totalMembers`, `ownerPetId` |
| `GroupService.isMember` | hook cho `PostPolicy` |

Lọc thành viên cho `members[]`/`totalMembers` đặt ở **mapper**, không ở mệnh đề `left join fetch` của
`GroupRepository`: một điều kiện đặt sai chỗ trong fetch join sẽ cắt luôn cả nhóm khỏi kết quả (xem
ghi chú có sẵn ở `GroupRepository.findAllDetailByIds`), còn lọc tại mapper thì `toDetail` và
`toSummary` dùng chung một câu trả lời.

**Không cần sửa** hai chỗ sau, và đó là chủ ý của thiết kế: `PostService.like`/`unlike` đã nạp bài qua
`postRepository.findVisibleById(...)`, còn quyền bình luận đã đi qua visibility của bài cha
(`CommentAccessGuard`). Siết nhánh D là đủ để "nhóm PRIVATE: người ngoài không tương tác được" đúng
cho cả đọc, thích và bình luận, không phải sửa từng endpoint.

---

## 7. Thông báo

Bốn loại mới trong `NotificationObjectType`, `objectId` của cả bốn là **id nhóm**:

| Loại | Người nhận |
|---|---|
| `GROUP_JOIN_REQUESTED` | từng quản trị nhóm (một bản ghi mỗi người — bảng là quan hệ một-người-nhận) |
| `GROUP_JOIN_APPROVED` | chủ của pet đã xin vào |
| `GROUP_INVITED` | chủ của pet được mời |
| `GROUP_INVITE_ACCEPTED` | chủ của pet đã mời |

Từ chối **không** sinh thông báo, khớp cách `FriendshipService.changeStatus` chỉ báo khi `ACCEPTED`.
Thêm loại mới đòi đủ ba bước như javadoc của enum đó ghi: nới enum MySQL, câu chữ ở
`NotificationPublisher`, đích đến ở frontend.

---

## 8. Mã lỗi module

| Tình huống | Mã | Ngoại lệ |
|---|---|---|
| Nhóm không tồn tại | 404 | `NotFoundException` |
| Không có yêu cầu / lời mời nào đang chờ | 404 | `NotFoundException` |
| Không phải thành viên (mời), không phải quản trị (duyệt) | 403 | `ForbiddenException` |
| Chỉ chủ nhóm mới xoá được nhóm | 403 | `ForbiddenException` |
| Chủ nhóm rời nhóm; rời nhóm mà không phải thành viên; tự mời chính mình | 400 | `BadRequestException` |
| Đã là thành viên; đã có yêu cầu/lời mời đang chờ | 409 | `ConflictException` |

Người ngoài đăng bài vào nhóm → **403 chứ không 404**: sự tồn tại của nhóm vốn đã công khai qua
`GET /v1/groups/{id}`, nên che giấu ở đây không giấu được gì. Khác với bài viết — ở đó 404 là bắt
buộc vì id bài là thứ duy nhất cần đoán để dò nội dung riêng tư.

---

## 9. Phân tầng

```
GroupController     HTTP + chọn mức guard. Không có quy tắc nghiệp vụ nào.
GroupService        Toàn bộ luật vòng đời thành viên + ranh giới transaction.
  requireActiveMember / requireManager / countActive...(OWNER)   ba mức quyền
  activate() / acceptInvite()                                    chuyển PENDING -> ACTIVE
  viewerStatusOf()                                               quan hệ người xem <-> nhóm
  GroupMapper                                                    lọc ACTIVE trước khi map
GroupMemberRepository  hai họ truy vấn, xem §6
PostPolicy             ai đăng bài vào nhóm được (phía GHI)
PostVisibility         ai đọc bài trong nhóm được (phía ĐỌC)
```

---

## 10. Lệch thiết kế đã phát hiện

### 10.1 `PUT /v1/groups/{id}` thiếu `type` thì nhóm thành PRIVATE

`GroupController.modified()` tính `"PUBLIC".equals(type) ? PUBLIC : PRIVATE`, nên một request sửa
**tên** mà không gửi `type` sẽ lật nhóm sang PRIVATE. Đây là hành vi thật của backend TS, được giữ lại
có chủ đích cho tới nay.

**Nên đổi.** Từ bản này tính riêng tư của nhóm chặn cả đường đọc bài lẫn đường vào nhóm, nên một lần
sửa tên vô tình trở thành một sự kiện ẩn toàn bộ nội dung nhóm khỏi người ngoài. Cách sửa: truyền
`type` xuống nguyên trạng và để `GroupService.modify` bỏ qua nhờ `if (type != null)` đã có sẵn — đây
là một **break có chủ đích** khỏi TS parity, nên tách thành thay đổi riêng.

### 10.2 ĐÃ SỬA — `PetContext` luôn null trên đường đọc

Bug được phát hiện khi thử luồng thật: **người vừa tạo một nhóm PRIVATE vẫn bị hiện nút "gửi yêu cầu
tham gia" vào nhóm của chính mình.**

`PetContextInterceptor.preHandle` thoát ngay khi route không mang `@RequirePet`/`@RequireAnyPet` và
không bao giờ đặt request attribute. Route đọc không thể mang `@RequirePet` (sẽ chặn luôn khách), nên
`PetContext.optional()` **luôn trả null trên mọi route đọc** — việc thoát sớm xảy ra *trước* cả khi
đọc token, nên đăng nhập cũng không thay đổi gì. Ba hệ quả:

| Chỗ | Vỡ thế nào |
|---|---|
| `GET /v1/groups/{id}` | `viewerStatus` chỉ có thể là `NONE` → nhóm PRIVATE `restricted: true` với **cả chủ nhóm** |
| `GET /v1/posts*` | nhánh D của `PostVisibility` là **code chết** → thành viên không đọc được bài trong nhóm mình |
| `GET /v1/comments/{postId}` | y hệt, qua `CommentController` |

Hỏng theo chiều **đóng** — không rò rỉ gì ra ngoài, nhưng nhóm riêng tư thành ra không dùng được.

**Bản vá**: interceptor xác nhận `X-Pet-Id` bất cứ khi nào header có mặt, trên mọi route, nhưng chỉ
*bắt buộc* nó ở `@RequirePet` (`resolveOptionalPet`). Bước so quyền sở hữu với tài khoản trong token
giữ nguyên, nên vẫn không ai mạo danh thành viên được; header sai thì bị bỏ qua thay vì ném lỗi.

**Điểm mù đã để lọt bug này**: 26 test của `GroupMembershipIntegrationTest` dùng
`PetContextTestSupport.actAs()`, tức ghi thẳng request attribute và **đi vòng qua interceptor** — test
service xanh trong khi route HTTP thật vỡ. `GroupPrivacyHttpIntegrationTest` được thêm để bịt đúng chỗ
đó: nó đi qua MockMvc với header thật, và đã được xác nhận là **đỏ khi bỏ bản vá** (`expected:<MEMBER>
but was:<NONE>`).

### 10.3 Cách ly database test

Hai lỗi hạ tầng test lộ ra trong lúc sửa, cả hai đều không nằm ở code nghiệp vụ:

- **Redis dùng chung.** `PetOwnerResolver` cache theo khoá `pet:owner:<petId>`. Mỗi lớp test có
  database MySQL riêng nên `petId` đếm lại từ 1 và trùng nhau giữa các lớp — và trùng cả với database
  dev đang chạy trên cùng Redis. Lớp chạy sau đọc được chủ sở hữu của pet thuộc lớp khác, biểu hiện là
  `expected: 4 but was: 2`. Sửa bằng `IntegrationTestBase.redisUrl(int)`: db 0 để cho dev, mỗi lớp phụ
  thuộc resolver nhận một index riêng.
- **`PostVisibilityIntegrationTest` không lặp lại được.** Nội dung bài là chuỗi cố định và mọi khẳng
  định so khớp đúng danh sách đó, nhưng database test tồn tại qua nhiều lần chạy nên lần thứ hai mỗi
  bài có hai bản. Sửa bằng `postRepository.deleteAll()` mở đầu `seed()`.

Ngoài ra `lopet_java_*_test` từng giữ `group_members.account_id NOT NULL` và khoá chính
`(account_id, group_id)` — di sản trước refactor pet mà `ddl-auto: update` không xoá nổi. Đã xử lý
bằng cách xoá các database đó cho Hibernate dựng lại từ entity hiện tại.

### 10.4 `ddl-auto`

Dự án không có Flyway/Liquibase; schema do Hibernate sinh với `ddl-auto: update` ở dev/test và
`validate` ở prod. `update` tự thêm được hai cột mới, nhưng **không nới được enum**
`notifications.objectType`. Chạy `scripts/group-membership-status-migration.sql` **trước** khi khởi
động phiên bản mới.

---

## 11. Test

Hai bộ, và **phải có cả hai** — lý do ở §10.2:

`GroupMembershipIntegrationTest` — 26 test, database `lopet_java_group_test`, tầng SERVICE với
`PetContextTestSupport.actAs`. Phủ luật nghiệp vụ, nhưng đi vòng qua interceptor nên **không** nói được
gì về route HTTP.

`GroupPrivacyHttpIntegrationTest` — 11 test, database `lopet_java_groupprivacy_test`, Redis db 3, đi
qua MockMvc với header `Authorization` + `X-Pet-Id` THẬT. Đây là bộ chặn tái diễn bug §10.2; đã xác
nhận nó đỏ khi bỏ bản vá. Bốn test trong đó là chốt chống mạo danh: vô danh hoặc người ngoài gửi
`X-Pet-Id` của một thành viên vẫn phải bị che.

Hai test canh đúng lỗ hổng của §6 và không được xoá:

- `khong_thay_bai_khi_dang_cho` — pet vừa xin vào nhóm PRIVATE **không** đọc được bài nào trong đó
  (nhánh D của `PostVisibility`);
- `khong_dem_vao_danh_sach` — hàng PENDING không vào `members[]` cũng không vào `totalMembers`.

Phần còn lại phủ: vào nhóm PUBLIC/PRIVATE, duyệt/từ chối/huỷ yêu cầu, ai được duyệt, hộp thư yêu cầu
không lẫn lời mời, quản trị nhóm không duyệt được lời mời, mời/chấp nhận/từ chối/mời lại, tham gia
khi đang được mời, `viewerStatus`, `restricted`, và rời nhóm (kể cả OWNER → 400).
