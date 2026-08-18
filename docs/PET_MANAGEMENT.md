# PET_MANAGEMENT

Pet Management Core — thú cưng, hồ sơ công khai của nó, và cơ chế "đang thao tác nhân danh pet nào".
Đây là module **mới**, không có bản tương ứng bên `lopet-be` (TypeScript), nên không xuất hiện trong
`API_MIGRATION_MATRIX.md`.

Phạm vi bản này: CRUD thú cưng + hồ sơ công khai + nền sở hữu + tầng `X-Pet-Id`. **Chưa** có Pet
Follow, Pet Post, Pet Memory, Pet Health, Lost Pet, Nearby, Recommendation — xem §5 và §9 để biết
chỗ nào đã chừa sẵn cho chúng.

---

## 1. Mô hình dữ liệu

```
accounts                    pets                          pet_profiles
   id  <------------------  account_id (FK, NOT NULL)         id  (PK)
                            id  (PK)  <-------------------- pet_id  (FK, UNIQUE)
                            name / species / breed             handle (UNIQUE)
                            gender / date_of_birth             display_name / avatar_url
                            status                             cover_url / bio
                            createdAt/updatedAt/deletedAt      status / visibility
                                                               createdAt/updatedAt/deletedAt
```

Ba ranh giới, mỗi cái trả lời một câu hỏi khác nhau:

| Bảng | Trả lời | Ai đọc được |
|---|---|---|
| `account_profiles` | *chủ tài khoản là ai* | **chỉ chính chủ** |
| `pets` | *con vật này là gì, của ai* | chủ sở hữu (+ người xem qua bộ lọc) |
| `pet_profiles` | *nó xuất hiện thế nào trên mạng xã hội* | công khai, có lọc `visibility` |

Trộn `pets` và `pet_profiles` làm một bảng thì mỗi lần trả hồ sơ công khai đều phải nhớ loại bỏ tay
các cột riêng tư. Tách ra thì "cái gì công khai" là một sự thật của schema, không phải một quy ước
mà mọi mapper phải nhớ.

### `pets.id` là danh tính, `pet_profiles.id` thì không

**Mọi bảng nội dung xã hội trỏ FK vào `pets.id`**, không phải `pet_profiles.id`. Hồ sơ công khai là
thứ đổi được và khoá được (đổi handle, ẩn hồ sơ, DEACTIVATED); khoá ngoại thì phải trỏ vào một danh
tính bất biến. Trỏ vào `pet_profiles.id` nghĩa là mỗi lần kiểm duyệt khoá một hồ sơ lại kéo theo câu
hỏi phải làm gì với hàng nghìn bài viết đang tham chiếu nó.

### Sở hữu là 1:N, không còn đồng sở hữu

Bảng nối `pet_ownerships` (`PRIMARY_OWNER` / `CO_OWNER`) **đã bị bỏ**. Quyền sở hữu giờ là cột
`pets.account_id NOT NULL`.

Lý do không phải là đơn giản hoá cho gọn: câu hỏi *"petId trong header có thuộc accountId trong token
không?"* được hỏi ở **mọi request tương tác**. Với bảng nối, mỗi lần hỏi là một truy vấn `EXISTS`
trên bảng n-n; với cột khoá ngoại, nó là một lần đọc theo khoá chính và cache được vào Redis bằng một
cặp khoá-giá trị. Xem §6.

Migration: `scripts/pet-profile-refactor-migration.sql`, bước 4 giữ lại `PRIMARY_OWNER` và **bỏ mọi
quan hệ `CO_OWNER`**.

## 2. Enum

| Enum | Giá trị | Cột | Thuộc |
|---|---|---|---|
| `PetSpecies` | DOG, CAT, BIRD, RABBIT, HAMSTER, FISH, REPTILE, OTHER | `varchar(30)` | `pets` |
| `PetGender` | MALE, FEMALE, UNKNOWN | `varchar(20)` | `pets` |
| `PetStatus` | ACTIVE, DEACTIVATED | `varchar(20)` | `pets` |
| `PetProfileStatus` | ACTIVE, DEACTIVATED | `varchar(20)` | `pet_profiles` |
| `PetVisibility` | PUBLIC, FOLLOWERS, PRIVATE | `varchar(20)` | `pet_profiles` |

`PetStatus.ARCHIVED` cũ đã đổi thành `DEACTIVATED` để hai bảng nói cùng một từ vựng.

Mọi trường enum đều kèm `@JdbcTypeCode(VARCHAR)`: từ Hibernate 6, `@Enumerated(STRING)` trên MySQL
sinh ra kiểu `enum('A','B')` native chứ không phải varchar, và `length` bị bỏ qua — thêm một loài mới
sẽ phải `ALTER TABLE`.

## 3. Endpoint

Base path `/v1` (không có `/api`), theo đúng toàn bộ controller còn lại.

### Thú cưng — dữ liệu sinh học, chỉ chủ sở hữu

| Method | Endpoint | Auth | Permission | Ownership |
|---|---|---|---|---|
| POST | `/v1/pets` | req | `pet:create` | chủ = người trong token |
| GET | `/v1/pets/me` | req | — | ownerId lấy từ token |
| GET | `/v1/pets/{petId}` | opt | — | `PetProfileVisibilityFilter` |
| PUT | `/v1/pets/{petId}` | req | `pet:update:own` | `pets.account_id` |
| DELETE | `/v1/pets/{petId}` | req | `pet:delete:own` | `pets.account_id` |

### Hồ sơ công khai — đường đọc duy nhất cho người lạ

| Method | Endpoint | Auth | Permission | Ownership |
|---|---|---|---|---|
| GET | `/v1/pet-profiles/handle/{handle}` | opt | — | `PetProfileVisibilityFilter` |
| GET | `/v1/pet-profiles/{petId}` | opt | — | `PetProfileVisibilityFilter` |
| GET | `/v1/pet-profiles/{petId}/owned` | req | — | `PetAccessGuard` |
| PUT | `/v1/pet-profiles/{petId}` | req | `petProfile:update:own` | `PetAccessGuard` |

**Không có endpoint tạo hồ sơ.** Hồ sơ ra đời cùng con vật, trong cùng transaction — xem §5.

`GET /v1/pets/me` **không** nhận `?userId=`. Danh tính luôn đến từ token — nhận id từ query param sẽ
biến endpoint này thành công cụ liệt kê hồ sơ riêng tư của bất kỳ ai.

Chưa phân trang, đúng như mọi endpoint danh sách hiện có của dự án.

### Ví dụ tạo thú cưng

```jsonc
// POST /v1/pets
{
  "name": "Milo",
  "species": "DOG",           // không phân biệt hoa thường
  "breed": "Golden Retriever",
  "gender": "MALE",
  "dateOfBirth": "2023-03-12",
  "visibility": "PUBLIC"      // của HỒ SƠ, bỏ trống → PUBLIC
}
```

Request **không có** `ownerId`, `status`, `id`, `bio`, `handle`, `displayName`, `createdAt`,
`updatedAt`, `deletedAt` — chúng không tồn tại trong record DTO, nên client không có đường nào gửi
lên. `bio`/`handle`/`displayName` sửa qua `PUT /v1/pet-profiles/{petId}`; hai đường ghi vào cùng một
cột là chỗ dữ liệu bắt đầu lệch.

`visibility` là trường DUY NHẤT của hồ sơ mà luồng tạo pet nhận — người dùng phải chọn được phạm vi
riêng tư ngay lúc tạo, không thể bắt họ tạo hồ sơ PUBLIC rồi mới đổi.

### `handle`

Regex `^[a-z0-9_]{3,30}$`, chuẩn hoá về chữ thường trước khi so trùng và trước khi lưu.

- **Không cho dấu chấm và gạch ngang**: handle đi vào URL và vào cú pháp `@mention`, mà ở cả hai nơi
  đó chúng là ký tự phân tách — `@milo.dog` không có cách nào biết handle kết thúc ở đâu.
- **Chuẩn hoá chữ thường** vì collation mặc định của MySQL không phân biệt hoa thường: `Milo` và
  `milo` sẽ là hai handle khác nhau ở tầng ứng dụng nhưng đụng UNIQUE index ở tầng DB — lỗi hiện ra
  thành 500 chứ không phải 409.
- Handle mặc định là `pet_<id>`, sinh sau khi `pets.id` có giá trị. Không lấy theo tên vì tên cho
  phép dấu tiếng Việt, khoảng trắng và trùng lặp — ba thứ handle đều cấm.

## 4. Quyền xem

`PetProfileVisibilityFilter.VISIBLE_TO` là mảnh JPQL duy nhất trả lời "ai xem được hồ sơ nào", đối
xứng với `PostVisibility` của module bài viết. **Module `pet` dùng chung đúng hằng số này** — phạm vi
riêng tư là thuộc tính của hồ sơ, hai module không được có hai câu trả lời khác nhau cho cùng câu hỏi.

```
A. pp.visibility = PUBLIC       -> mọi người, kể cả khách chưa đăng nhập
B. p.account.id = :viewerId     -> chủ sở hữu, mọi visibility
```

**FOLLOWERS hiện hành xử y hệt PRIVATE.** Đồ thị follow giữa các pet chưa tồn tại, nên chọn fail
CLOSED: coi FOLLOWERS là công khai cho tới khi có bảng follow sẽ làm rò rỉ đúng những hồ sơ mà người
dùng đã chủ động thu hẹp. Khi bảng follow ra đời, thêm một nhánh `exists` vào **đúng hằng số đó** là
xong — không endpoint nào phải sửa.

Hồ sơ không xem được trả **404, không phải 403**: phản hồi phải giống hệt trường hợp không tồn tại,
nếu không endpoint thành công cụ quét id để biết hồ sơ PRIVATE nào đang tồn tại.

## 5. Bất biến "Pet luôn có PetProfile"

`PetService.create` dựng cả hai trong **một** `@Transactional`. Thứ tự bắt buộc: `save(pet)` trước,
vì handle mặc định sinh từ `pet.id`.

Nếu bước tạo hồ sơ hỏng thì bản ghi `pets` cũng rollback. Một con vật không có hồ sơ sẽ biến mất khỏi
mọi luồng đọc (chúng đều join `pet_profiles` để lấy phạm vi riêng tư) mà chủ của nó không có endpoint
nào để sửa tình trạng đó.

Bất biến được giữ ở **ba tầng**, mỗi tầng bịt một lỗ khác nhau:

1. `PetService.create` — cùng transaction;
2. `Pet.petProfile` khai `cascade = ALL, orphanRemoval = true` — hồ sơ không có vòng đời riêng;
3. `pet_profiles.pet_id` UNIQUE NOT NULL — tầng ứng dụng có bug gì thì DB vẫn giữ.

`PetProfileService` **không có** hàm `create` public. Mở một đường tạo hồ sơ rời sẽ đẻ lại đúng lỗ
hổng mà module hồ sơ chủ tài khoản đã phải refactor để bịt (hồ sơ mồ côi, và hồ sơ gắn nhầm chủ).

## 6. `X-Pet-Id` — tầng phân quyền thứ ba

Ba câu hỏi khác nhau, ba cơ chế khác nhau:

| Cơ chế | Câu hỏi |
|---|---|
| `@RequirePermission` | *hành động này có được phép không?* |
| `OwnershipGuard` / `*AccessGuard` | *được phép làm lên ĐÚNG tài nguyên này không?* |
| `@RequirePet` / `@RequireAnyPet` | *đang hành động NHÂN DANH ai?* |

### Vì sao KHÔNG nhúng petId vào JWT

Token chỉ mang `accountId`. Một người có nhiều pet và đổi qua lại giữa chúng liên tục; nhét petId vào
token nghĩa là mỗi lần đổi pet phải cấp lại token, và một token cũ bị rò rỉ vẫn hành động được nhân
danh pet đã chuyển chủ.

### Hai annotation, hai hợp đồng API

| Annotation | Cần `X-Pet-Id`? | Dùng cho |
|---|---|---|
| `@RequirePet` | **bắt buộc** | pet là tác nhân: post, comment, like, thành viên nhóm |
| `@RequireAnyPet` | không đòi | tác nhân vẫn là tài khoản: nhắn tin, kết bạn |
| *(không đánh dấu)* | tuỳ chọn | đường ĐỌC: header có thì mở rộng phạm vi thấy được |

Yêu cầu một header mà không dùng tới giá trị của nó là hợp đồng API nói dối — client sẽ gửi bừa một
petId, và ngày nào đó sẽ có người tin rằng giá trị ấy mang ý nghĩa.

**"Không đòi" khác "bỏ qua".** Hàng thứ ba là đường đọc, và ở đó `X-Pet-Id` vẫn được xác nhận rồi đưa
vào `PetContext` nếu có — chỉ khác là thiếu nó thì người gọi xem ở mức tài khoản chứ không bị chặn.
Trước bản vá interceptor thoát ngay khi route không mang annotation nào, nên `PetContext.optional()`
**luôn null trên toàn bộ đường đọc**: nhánh "bài trong nhóm mà pet này là thành viên" của
`PostVisibility` thành code chết, và `GET /v1/groups/{id}` chỉ trả được `viewerStatus = NONE` — chủ
nhóm bị coi là người ngoài nhóm của chính mình. Xem `docs/GROUP_MANAGEMENT.md` §10.2.

Trên đường đọc, header sai (không xác thực được, không phải số, pet của người khác) bị **bỏ qua** chứ
không ném lỗi: người gọi tụt về đúng phạm vi của người chưa chọn pet. Bước so quyền sở hữu vẫn giữ
nguyên, nên không ai mạo danh thành viên bằng cách tự khai `X-Pet-Id` được.

### Luồng và mã lỗi

`PetContextInterceptor` chạy **sau** `AuthInterceptor` (`order(0)` / `order(1)` trong `WebConfig`):
nó cần accountId đã xác thực để so quyền sở hữu.

| Tình huống | Mã | Ngoại lệ |
|---|---|---|
| Thiếu `X-Pet-Id`, tài khoản CÓ pet | **400** | `MissingPetHeaderException` |
| Header không phải số | 400 | `BadRequestException` |
| Tài khoản chưa sở hữu pet nào | **403** | `NoPetOwnedException` |
| Pet không tồn tại / của người khác / đã ngừng | **403** | `PetNotOwnedException` |

Ba mã cố ý không gộp. Thiếu header là request **dị dạng**, không phải request bị từ chối — trả 403 sẽ
khiến client tưởng token hết hiệu lực và đá người dùng ra màn hình đăng nhập. "Chưa có pet nào" là
trạng thái người dùng **sửa được**, phải hướng họ sang luồng tạo pet thay vì báo "không có quyền".
Còn "không tồn tại" và "của người khác" thì phải giống hệt nhau, nếu không chúng thành công cụ quét
id.

Cả ba đều `extends HttpException` nên `GlobalExceptionHandler` sẵn có đã bắt — không cần thêm
`@ExceptionHandler` nào.

### Cache Redis

Khoá `pet:owner:<petId>` → accountId, TTL **30 phút**, cùng quy ước đặt tên với `OtpStore`.

- **Invalidate ngay** khi ngừng hoạt động pet (`PetService.deactivate`) hoặc đổi chủ. Bỏ bước này thì
  một pet đã tắt vẫn qua được validate cho tới khi TTL hết hạn.
- **Không cache giá trị âm**: petId không tồn tại thì mỗi lần hỏi lại đi xuống DB. Cache miss âm là
  bề mặt để ai đó bơm hàng triệu id rác vào Redis, mà truy vấn tra chủ chỉ đọc một cột theo khoá
  chính nên chi phí gần bằng không.
- **Redis hỏng không làm hỏng request**: mọi lỗi đọc/ghi cache đều bị nuốt và rơi về DB. Cache là thứ
  tăng tốc, không phải nguồn sự thật.

### Kênh Socket.IO

`SocketPetContext` là bản đối xứng cho kênh socket, dùng **chung** `PetOwnerResolver` nên cùng cache
và cùng bị vô hiệu hoá bởi cùng một lời gọi `invalidate`. Client gửi `petId` kèm payload; `accountId`
thì lấy từ `SocketIoConfig.USER_ID_KEY` (server gắn vào sau khi kiểm JWT lúc handshake), không bao
giờ từ payload.

> **Chưa handler nào gọi tới nó.** Hai sự kiện đến hiện có (`message delivered`, `message read`) thao
> tác trên tin nhắn, mà tin nhắn vẫn thuộc về TÀI KHOẢN. Lớp này viết sẵn cùng lúc với kênh REST vì
> đó là cách duy nhất đảm bảo hai kênh không trôi khác nhau.

## 7. Xoá = xoá mềm

`DELETE /v1/pets/{petId}` ghi **cả hai** cột, trên **cả hai** bảng, trong một transaction:

- `deletedAt = now()` — thứ thực sự khiến bản ghi biến mất khỏi mọi truy vấn, qua `@SQLRestriction`
  trên entity. Không endpoint nào đi vòng qua được vì Hibernate chèn điều kiện này vào mọi câu lệnh.
- `status = DEACTIVATED` — để bản ghi tự mô tả được trạng thái khi đọc thẳng trong DB.

Chỉ ghi `status` thì hồ sơ vẫn hiện ra ở mọi luồng đọc; chỉ ghi `deletedAt` thì một hàng "đã xoá" lại
mang `status = ACTIVE`.

Hồ sơ công khai phải tắt **cùng lúc** với con vật: hồ sơ còn sống sau khi con vật đã tắt thì
`GET /v1/pet-profiles/handle/{handle}` vẫn trả về nó.

Không xoá cứng vì bài viết và bình luận của **người khác** đang trỏ tới `pets.id` — xoá cứng là phá
vỡ nội dung không thuộc về người bấm nút.

> **Đây là module ĐẦU TIÊN của dự án thực sự xoá mềm.** 16 bảng khác đều kế thừa `BaseEntity` và mang
> `@SQLRestriction`, nhưng không luồng nghiệp vụ nào ghi vào `deletedAt` — mọi thao tác xoá đều là xoá
> cứng (`PostService.delete` gọi thẳng `repository.delete`). Hạ tầng xoá mềm đã có sẵn từ trước, Pet
> là chỗ đầu tiên dùng tới nó.

## 8. Phân tầng

```
PetController          HTTP + lấy danh tính từ token. Không có quy tắc nghiệp vụ nào.
PetAccessGuard         Tầng ownership (404 nếu không nạp được, 403 nếu không phải chủ).
PetService             Toàn bộ quy tắc nghiệp vụ + ranh giới transaction.
PetPolicy              Chuẩn hoá dữ liệu sinh học (parse enum, trim, chặn ngày tương lai).
PetRepository          Truy vấn đã lọc quyền xem.

PetProfileController   HTTP. Module DUY NHẤT có endpoint đọc công khai trong nhánh Account/Pet.
PetProfileService      Quy tắc của hồ sơ công khai. Không có create() public.
PetProfilePolicy       Chuẩn hoá handle / displayName / visibility.
PetProfileFactory      Nguồn duy nhất sinh row pet_profiles.
PetProfileRepository   Truy vấn đã lọc quyền xem + kiểm trùng handle.

PetContextInterceptor  Xác nhận X-Pet-Id ở biên HTTP.
PetContext             Truy cập "pet đang thao tác" — đối xứng CurrentUser.
PetOwnerResolver       petId -> accountId, cache Redis, dùng chung REST + Socket.IO.
```

`PetAccessGuard` và `PetContextInterceptor` trả lời hai câu hỏi khác nhau: guard hỏi *"người gọi có
được sửa TÀI NGUYÊN trên URL không"*, interceptor hỏi *"người gọi đang hành động nhân danh pet nào"*.
Một endpoint quản trị pet cần cái đầu; một endpoint đăng bài cần cái sau.

Interceptor là tầng chặn ở **biên**, không thay thế guard trong service: một use case gọi thẳng từ nơi
khác (job nền, handler socket) không đi qua interceptor nào cả.

### Mã lỗi module

| Tình huống | Mã | Ngoại lệ |
|---|---|---|
| Sai ràng buộc DTO | 400 `Validation error` | `MethodArgumentNotValidException` |
| species/gender/visibility lạ, tên rỗng, handle sai định dạng, ngày sinh tương lai | 400 | `BadRequestException` |
| Chưa xác thực | 403 | `ForbiddenException` |
| Không phải chủ sở hữu | 403 | `ForbiddenException` |
| Không tồn tại **hoặc** không được xem | 404 | `NotFoundException` |
| Handle đã có người dùng | 409 | `ConflictException` |

Không dùng 401 và 422: dự án không có tiền lệ cho hai mã đó ở luồng nghiệp vụ. 401 chỉ xuất hiện ở
`AuthInterceptor` khi token hỏng trên route `optionalAuth`.

## 9. Lệch thiết kế đã phát hiện

### 9.1 `date_of_birth` NOT NULL

Yêu cầu nghiệp vụ muốn cho phép "không rõ ngày sinh" — rất thường gặp với thú cưng nhận nuôi hoặc cứu
hộ. Schema hiện tại khai `date_of_birth date NOT NULL`.

**Đã cố ý KHÔNG tự đổi schema.** Muốn cho phép bỏ trống thì cần, theo thứ tự:

1. `ALTER TABLE pets MODIFY date_of_birth date NULL;`
2. bỏ `nullable = false` ở `Pet.dateOfBirth`;
3. bỏ `@NotNull` ở `CreatePetRequest`/`UpdatePetRequest`;
4. đổi `requireDateOfBirth` thành chỉ kiểm "không ở tương lai" khi khác null.

### 9.2 `updatedAt` nullable

DBML ghi `updatedAt datetime(6) NOT NULL`, nhưng `BaseEntity.updatedAt` cố ý khai nullable (xem
javadoc tại `BaseEntity.java`) và dùng chung cho toàn bộ bảng. Trên thực tế `@PrePersist` luôn gán
giá trị nên cột không bao giờ NULL.

### 9.3 `ddl-auto`

Dự án không có Flyway/Liquibase; schema do Hibernate sinh với `ddl-auto: update`. Những thay đổi mà
`update` KHÔNG tự làm được — đổi tên bảng/cột, xoá bảng/cột — nằm ở script tay trong `scripts/`. Chạy
`scripts/pet-profile-refactor-migration.sql` **trước** khi khởi động phiên bản mới.

### 9.4 Nội dung xã hội vẫn trỏ vào `accounts.id`

`posts`, `post_likes`, `comments`, `group_members` **chưa** đổi FK sang `pet_id` — đó là giai đoạn 2
của refactor. Vì vậy `@RequirePet` hiện xác nhận `X-Pet-Id` nhưng giá trị chưa được dùng làm tác giả:
tầng chặn đã đúng chỗ, chỉ còn phần ghi dữ liệu chưa chuyển. `friend_ships`, `messages`,
`notifications`, `reports`, `advertiser_profiles` thì **ở lại** phạm vi tài khoản vĩnh viễn.

## 10. Test

| File | Loại | DB riêng |
|---|---|---|
| `PetManagementIntegrationTest` | tích hợp, MySQL thật | `lopet_java_pet_test` |
| `PetProfileIntegrationTest` | tích hợp, MySQL thật | `lopet_java_petprofile_test` |
| `PetContextIntegrationTest` | tích hợp, MySQL + Redis thật | `lopet_java_petcontext_test` |
| `PetRequestValidationTest` | unit, `jakarta.validation.Validator` | — |

Chạy: `docker compose up -d mysql-docker redis` rồi `./mvnw test -Dtest='Pet*'`.
