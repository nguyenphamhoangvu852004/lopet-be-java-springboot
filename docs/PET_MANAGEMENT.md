# PET_MANAGEMENT

Pet Management Core — hồ sơ thú cưng và quan hệ sở hữu. Đây là module **mới**, không có bản
tương ứng bên `lopet-be` (TypeScript), nên không xuất hiện trong `API_MIGRATION_MATRIX.md`.

Phạm vi bản này: CRUD hồ sơ + nền sở hữu. **Chưa** có Pet Follow, Pet Post, Pet Memory, Pet Health,
Lost Pet, Nearby, Recommendation — xem §5 để biết chỗ nào đã chừa sẵn cho chúng.

---

## 1. Mô hình dữ liệu

```
accounts                pet_ownerships                 pets
   id  <---------------  user_id  (PK, FK)              id  (PK)
                         pet_id   (PK, FK)  ----------> name / species / breed / gender
                         ownership_type                 date_of_birth / bio
                         createdAt                      status / visibility
                                                        createdAt / updatedAt / deletedAt
```

Quan hệ sở hữu là **n-n qua bảng nối**, không phải cột `owner_id` trên `pets`. Một thú cưng có
đúng 1 `PRIMARY_OWNER` và 0..N `CO_OWNER`.

| Bảng | Kế thừa `BaseEntity` | `@SQLRestriction` |
|---|---|---|
| `pets` | có (createdAt/updatedAt/deletedAt) | `deletedAt is null` |
| `pet_ownerships` | **không** — bảng chỉ có `createdAt` | không |

`pet_ownerships` không kế thừa `BaseEntity` theo đúng tiền lệ `post_likes`: mất quyền sở hữu là xoá
hẳn bản ghi, nên "tồn tại bản ghi" đã đúng nghĩa là đang sở hữu.

### Vì sao `ownership_type` KHÔNG nằm trong khoá chính

PK là `(pet_id, user_id)` — nghĩa là *một người giữ đúng một vai trò trên một thú cưng*. Thêm
`ownership_type` vào PK để "chặn hai PRIMARY_OWNER" là hiểu sai ràng buộc: nó không chặn được gì cả
(hai người khác nhau vẫn cùng là PRIMARY), mà còn cho phép đúng cái trạng thái vô nghĩa — một người
vừa PRIMARY vừa CO_OWNER. Quy tắc một-chủ-chính được đặt ở `PetService.addOwner`, cửa duy nhất ghi
vào bảng này.

## 2. Enum

| Enum | Giá trị | Cột |
|---|---|---|
| `PetSpecies` | DOG, CAT, BIRD, RABBIT, HAMSTER, FISH, REPTILE, OTHER | `varchar(30)` |
| `PetGender` | MALE, FEMALE, UNKNOWN | `varchar(20)` |
| `PetStatus` | ACTIVE, ARCHIVED | `varchar(20)` |
| `PetVisibility` | PUBLIC, FOLLOWERS, PRIVATE | `varchar(20)` |
| `PetOwnershipType` | PRIMARY_OWNER, CO_OWNER | `varchar(20)` |

Cột khai `varchar` chứ không phải MySQL `enum(...)`. Khác với `posts.postScope` và `group_members.role`
— hai cột đó giữ kiểu `enum` vì đang khớp với schema do TypeORM sinh ra, không đổi được.

Để ra được `varchar`, mỗi trường enum phải kèm `@JdbcTypeCode(SqlTypes.VARCHAR)`: **từ Hibernate 6,
`@Enumerated(STRING)` trên MySQL sinh ra kiểu `enum('A','B')` native** chứ không phải varchar, và
thuộc tính `length` bị bỏ qua hoàn toàn. Thiếu annotation đó thì `@Column(length = 30)` không có tác
dụng gì và schema lệch âm thầm so với thiết kế.

> **Lưu ý về CHECK constraint.** Hibernate tự sinh thêm `pets_chk_1..4` và `pet_ownerships_chk_1` giới
> hạn giá trị hợp lệ của từng cột. Đây là một lớp chặn tốt ở tầng DB, nhưng nó cũng có nghĩa là
> thêm một loài mới vào `PetSpecies` **vẫn cần động vào DB**: `ddl-auto: update` không sửa constraint
> đã tồn tại, phải `ALTER TABLE pets DROP CHECK pets_chk_2;` rồi để Hibernate tạo lại. So với kiểu
> `enum` native thì vẫn nhẹ hơn (không phải viết lại định nghĩa cột, dữ liệu không bị copy bảng),
> nhưng không phải là "không cần động gì".

## 3. Endpoint

Base path `/v1` (không có `/api`), theo đúng toàn bộ controller còn lại.

| Method | Endpoint | Auth | Permission | Ownership |
|---|---|---|---|---|
| POST | `/v1/pets` | req | `pet:create` | người tạo → PRIMARY_OWNER |
| GET | `/v1/pets/me` | req | — | ownerId lấy từ token |
| GET | `/v1/pets/{petId}` | opt | — | `PetVisibilityFilter` |
| PUT | `/v1/pets/{petId}` | req | `pet:update:own` | PRIMARY_OWNER **hoặc** CO_OWNER |
| DELETE | `/v1/pets/{petId}` | req | `pet:delete:own` | chỉ PRIMARY_OWNER |

`GET /v1/pets/me` **không** nhận `?userId=`. Danh tính luôn đến từ token — nhận id từ query param
sẽ biến endpoint này thành công cụ liệt kê hồ sơ riêng tư của bất kỳ ai.

Chưa phân trang, đúng như mọi endpoint danh sách hiện có của dự án (`getAll`, `getSuggestList`,
`getByAccountId`… đều trả `List<T>` trần).

### Ví dụ tạo hồ sơ

```jsonc
// POST /v1/pets
{
  "name": "Milo",
  "species": "DOG",           // không phân biệt hoa thường
  "breed": "Golden Retriever",
  "gender": "MALE",
  "dateOfBirth": "2023-03-12",
  "bio": "Always hungry.",
  "visibility": "PUBLIC"      // bỏ trống → PUBLIC
}
```

Request **không có** `ownerId`, `ownershipType`, `status`, `id`, `createdAt`, `updatedAt`,
`deletedAt` — chúng không tồn tại trong record DTO, nên client không có đường nào gửi lên.

## 4. Quyền xem

`PetVisibilityFilter.VISIBLE_TO` là mảnh JPQL duy nhất trả lời "ai xem được hồ sơ nào", đối xứng với
`PostVisibility` của module bài viết. Lọc nằm trong **câu truy vấn**, không phải sau khi đã nạp về.

```
A. visibility = PUBLIC                    -> mọi người, kể cả khách
--- khách chưa đăng nhập dừng ở đây ---
B. người xem có mặt trong pet_ownerships  -> mọi visibility, mọi ownership_type
```

**FOLLOWERS hiện hành xử y hệt PRIVATE.** Đồ thị follow chưa tồn tại (Pet Follow ngoài phạm vi),
nên chọn fail CLOSED: coi FOLLOWERS là công khai cho tới khi có bảng follow sẽ làm rò rỉ đúng những
hồ sơ mà người dùng đã chủ động thu hẹp. Khi bảng follow ra đời, thêm một nhánh `exists` vào **đúng
hằng số đó** là xong — không endpoint nào phải sửa.

Hồ sơ không xem được trả **404, không phải 403**: phản hồi phải giống hệt trường hợp không tồn tại,
nếu không endpoint thành công cụ quét id để biết hồ sơ PRIVATE nào đang tồn tại.

## 5. Xoá = xoá mềm

`DELETE /v1/pets/{petId}` ghi **cả hai** cột trong một transaction:

- `deletedAt = now()` — thứ thực sự khiến hồ sơ biến mất khỏi mọi truy vấn, qua `@SQLRestriction`
  trên entity. Không endpoint nào đi vòng qua được vì Hibernate chèn điều kiện này vào mọi câu lệnh.
- `status = ARCHIVED` — để bản ghi tự mô tả được trạng thái khi đọc thẳng trong DB.

Chỉ ghi `status` thì hồ sơ vẫn hiện ra ở mọi luồng đọc; chỉ ghi `deletedAt` thì một hàng "đã xoá"
lại mang `status = ACTIVE`.

> **Đây là module ĐẦU TIÊN của dự án thực sự xoá mềm.** 16 bảng khác đều kế thừa `BaseEntity` và
> mang `@SQLRestriction`, nhưng không luồng nghiệp vụ nào ghi vào `deletedAt` — mọi thao tác xoá đều
> là xoá cứng (`PostService.delete` gọi thẳng `repository.delete`). Hạ tầng xoá mềm đã có sẵn từ
> trước, Pet là chỗ đầu tiên dùng tới nó.

## 6. Phân tầng

```
PetController      HTTP + lấy danh tính từ token. Không có quy tắc nghiệp vụ nào.
PetAccessGuard     Tầng ownership (404 nếu không nạp được, 403 nếu không phải chủ).
PetService         Toàn bộ quy tắc nghiệp vụ + ranh giới transaction.
PetPolicy          Chuẩn hoá dữ liệu client gửi lên (parse enum, trim, chặn ngày tương lai).
PetRepository      Truy vấn đã lọc quyền xem.
```

Ba trường enum trong request khai kiểu `String` chứ không phải kiểu enum. Lý do là hành vi lỗi:
Jackson gặp giá trị enum lạ sẽ ném `HttpMessageNotReadableException`, mà `GlobalExceptionHandler`
không có nhánh cho ngoại lệ đó nên client nhận **500** thay vì 400. Nhận `String` rồi để `PetPolicy`
chuyển đổi cho ra 400 kèm danh sách giá trị hợp lệ — đúng cách `PostController` nhận `scope`.

### Mã lỗi

| Tình huống | Mã | Ngoại lệ |
|---|---|---|
| Sai ràng buộc DTO | 400 `Validation error` | `MethodArgumentNotValidException` |
| species/gender/visibility lạ, tên rỗng, ngày sinh tương lai | 400 | `BadRequestException` |
| Chưa xác thực | 403 | `ForbiddenException` |
| Không phải chủ sở hữu | 403 | `ForbiddenException` |
| Không tồn tại **hoặc** không được xem | 404 | `NotFoundException` |
| Đã có PRIMARY_OWNER / đã là chủ sở hữu | 409 | `ConflictException` |

Không dùng 401 và 422: dự án không có tiền lệ cho hai mã đó ở luồng nghiệp vụ. 401 chỉ xuất hiện ở
`AuthInterceptor` khi token hỏng trên route `optionalAuth`.

## 7. Lệch thiết kế đã phát hiện

### 7.1 `date_of_birth` NOT NULL

Yêu cầu nghiệp vụ muốn cho phép "không rõ ngày sinh" — rất thường gặp với thú cưng nhận nuôi hoặc
cứu hộ. Schema hiện tại khai `date_of_birth date NOT NULL`.

**Đã cố ý KHÔNG tự đổi schema.** Trường này đang là bắt buộc ở cả `@NotNull` trên DTO lẫn
`PetPolicy.requireDateOfBirth`. Muốn cho phép bỏ trống thì cần, theo thứ tự:

1. `ALTER TABLE pets MODIFY date_of_birth date NULL;`
2. bỏ `nullable = false` ở `Pet.dateOfBirth`;
3. bỏ `@NotNull` ở `CreatePetRequest`/`UpdatePetRequest`;
4. đổi `requireDateOfBirth` thành chỉ kiểm "không ở tương lai" khi khác null.

### 7.2 `updatedAt` nullable

DBML ghi `updatedAt datetime(6) NOT NULL`, nhưng `BaseEntity.updatedAt` cố ý khai nullable (xem
javadoc tại `BaseEntity.java`) và dùng chung cho 16 bảng. Trên thực tế `@PrePersist` luôn gán giá
trị nên cột không bao giờ NULL. Ép NOT NULL riêng cho `pets` sẽ phải bỏ `BaseEntity`.

### 7.3 `ddl-auto`

Dự án không có Flyway/Liquibase; schema do Hibernate sinh. `ddl-auto` đã được bật lại thành `update`
để hai bảng mới được tạo. Giá trị này áp cho **toàn bộ** entity, không riêng `pets` — nên backup DB
trước lần chạy đầu.

## 8. Test

| File | Loại | Số test |
|---|---|---|
| `PetManagementIntegrationTest` | tích hợp, MySQL thật, DB riêng `lopet_java_pet_test` | 38 |
| `PetRequestValidationTest` | unit, `jakarta.validation.Validator` | 11 |

Chạy: `docker compose up -d mysql-docker redis` rồi `./mvnw test -Dtest='Pet*'`.

Không mock repository — hai chỗ dễ sai nhất của module này đều nằm trong SQL: mệnh đề lọc quyền xem,
và việc `@SQLRestriction` có thật sự khiến hồ sơ đã lưu trữ biến mất khỏi mọi luồng đọc hay không.

Test đáng chú ý:

- `tao_ho_so_va_so_huu_la_mot_transaction` — rollback ở transaction NGOÀI rồi khẳng định cả `pets`
  lẫn `pet_ownerships` đều trống. Nếu `create()` mở transaction riêng thì bản ghi `pets` sẽ sống sót.
- `chi_mot_primary_owner` — `ConflictException`, và đếm lại đúng 1 PRIMARY_OWNER trong DB.
- `guard_khong_do_duoc_su_ton_tai` — hồ sơ PRIVATE của người khác cho **404** chứ không phải 403.
- `primary_owner_luu_tru_duoc` — đếm bằng SQL thô để chứng minh hàng vẫn còn (xoá mềm, không phải
  `DELETE`); mọi truy vấn JPA đều bị `@SQLRestriction` che nên không có cách nào khác kiểm được.
- `da_luu_tru_thi_bien_mat` — hồ sơ đã lưu trữ vắng mặt ở cả `getOneById`, `getOwnedBy` và
  `findById`.
