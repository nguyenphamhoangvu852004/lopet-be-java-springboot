# DATABASE_MIGRATION_NOTES

`lopet-be` chạy TypeORM với **`synchronize: true`** và **không có file migration nào**. Vì vậy
schema thật = 18 entity trong `src/entities/`. Java **giữ nguyên** tên bảng, tên cột, kiểu và ràng
buộc; mọi chỗ không map được 1:1 được ghi ở §4.

Quy ước đặt tên của TypeORM khi không khai `name`: **giữ nguyên tên property** (camelCase). Vì vậy
schema trộn lẫn `snake_case` (nơi có `@JoinColumn({name:...})`) và `camelCase` (`createdAt`,
`postScope`, `mediaUrl`, `coverUrl`…). Java phải giữ đúng như vậy, **không** đổi sang snake_case.

---

## 1. Cột kế thừa từ `BaseEntity`

```ts
@CreateDateColumn() createdAt   // datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
@UpdateDateColumn() updatedAt   // datetime(6) NULL ON UPDATE CURRENT_TIMESTAMP(6)
@DeleteDateColumn() deletedAt   // datetime(6) NULL
```

Áp dụng cho: `accounts`, `profiles`, `roles`, `permissions`, `account_role`, `groups`,
`group_members`, `posts`, `post_medias`, `comments`, `friend_ships`, `messages`, `notifications`,
`reports`, `advertisements`, `advertiser_profiles`.

**Ngoại lệ quan trọng — `post_likes`**: entity này `extends BaseEntity` **của TypeORM**
(`import { BaseEntity } from 'typeorm'`), không phải BaseEntity của dự án. Bảng `post_likes` do đó
**chỉ có 3 cột**: `id`, `post_id`, `account_id` — không có timestamp, không có soft delete.

## 2. Bảng & cột

### accounts
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| id | int AI | PK |
| email | varchar(255) | UNIQUE |
| username | varchar(255) | UNIQUE |
| password | varchar(255) | `select:false` ở tầng ORM |
| isBanned | tinyint | DEFAULT 0, nullable |
| profileId | int | FK → profiles.id, UNIQUE (OneToOne), ON DELETE CASCADE |
| createdAt/updatedAt/deletedAt | | |

### profiles
`id` PK; `fullName` varchar NULL; `phoneNumber` varchar NULL; `bio` text NULL; `sex` tinyint NULL;
`dateOfBirth` date NULL; `hometown` varchar NULL; `avatarUrl` text NULL; `coverUrl` text NULL.

### roles
`id` PK; `name` enum('ADMIN','MODERATOR','SUPPORT') UNIQUE; `description` varchar NULL.

### permissions
`id` PK; `code` varchar UNIQUE; `resource` varchar; `action` varchar;
`scope` enum('ANY','OWN') DEFAULT 'ANY'; `description` varchar NULL.

### role_permission (bảng nối do `@JoinTable` sinh)
`role_id` int, `permission_id` int — PK tổ hợp, FK CASCADE. **Không có cột audit.**

### account_role (entity tường minh)
`account_id` int, `role_id` int — **PK tổ hợp**; `granted_by` int NULL (FK accounts, ON DELETE SET
NULL); `granted_at` datetime DEFAULT CURRENT_TIMESTAMP; + 3 cột BaseEntity.

### groups
`id` PK; `name` varchar; `type` enum('PUBLIC','PRIVATE') NOT NULL (**không có default**);
`bio` varchar NULL; `coverUrl` text NOT NULL.
> `groups` là **từ khoá dành riêng của MySQL 8.0.2+** → mọi tham chiếu phải được quote bằng backtick.

### group_members
`group_id` int + `account_id` int — **PK tổ hợp**; `role` enum('OWNER','ADMIN','MEMBER') DEFAULT
'MEMBER'; `joined_at` datetime DEFAULT CURRENT_TIMESTAMP; + 3 cột BaseEntity.
> Bảng **không có cột trạng thái** (PENDING/BANNED/LEFT): rời nhóm = xoá bản ghi, nên "tồn tại bản
> ghi" đồng nghĩa "đang là thành viên".

### posts
`id` PK; `account_id` int (FK accounts, CASCADE); `content` text; `group_id` int NULL (FK groups,
CASCADE); `postType` enum('GROUP','USER') **NULL**; `postScope` enum('PUBLIC','FRIEND','PRIVATE')
NOT NULL DEFAULT 'PUBLIC'.

### post_medias
`id` PK; `mediaUrl` text; `mediaType` enum('VIDEO','IMAGE'); `post_id` int NOT NULL (CASCADE).

### post_likes
`id` PK; `post_id` int (CASCADE); `account_id` int (CASCADE). **Không UNIQUE(post_id, account_id)** —
tính duy nhất chỉ được bảo đảm ở tầng service.

### comments
`id` PK; `text` text; `parentId` int NULL (FK comments, CASCADE); `images` text NULL;
`post_id` int NOT NULL (CASCADE); `account_id` int NOT NULL (CASCADE).

### friend_ships
`id` PK; `sender_id` int; `receiver_id` int; `status`
enum('PENDING','ACCEPTED','REJECTED','BLOCKED') DEFAULT 'PENDING'.
**Không có UNIQUE(sender_id, receiver_id)** — cặp ngược chiều có thể tồn tại song song.

### messages
`id` PK; `sender_id` int; `receiver_id` int; `content` text; `mediaUrl` text;
`status` enum('SENT','DELIVERED','READ') DEFAULT 'SENT'.

### notifications
`id` PK; `actorId` int NOT NULL; `receptorId` int NOT NULL; `content` text NOT NULL;
`objectType` enum('POST','MESSAGE') NOT NULL; `status` enum('SENT','DELIVERED','READ') DEFAULT 'SENT'.

### reports
`id` PK; `reporter_id` int NOT NULL (CASCADE); `reason` text; `target_type`
enum('USER','GROUP','POST') NOT NULL; `target_id` **int** NOT NULL; `action`
enum('PENDING','CANCELLED','APPROVED') DEFAULT 'PENDING'; `resolved_by` int NULL (SET NULL);
`resolved_at` datetime NULL.

### advertisements
`id` PK; `advertiser_id` int NOT NULL → **`advertiser_profiles.id`** (không phải accounts), CASCADE;
`title` varchar; `description` text; `image_url` text; `link_reference` text;
`status` enum('DRAFT','REVIEW','ACTIVE','REJECTED') DEFAULT 'DRAFT'.

### advertiser_profiles
`id` PK; `account_id` int NOT NULL UNIQUE (OneToOne, CASCADE);
`status` enum('PENDING','APPROVED','SUSPENDED') DEFAULT 'PENDING'; `company_name` varchar NULL;
`balance` decimal(12,2) DEFAULT 0; `daily_limit` decimal(12,2) NULL; `approved_by` int NULL (SET
NULL); `approved_at` datetime NULL.

## 3. Sơ đồ quan hệ

```
accounts 1—1 profiles                     (FK nằm ở accounts.profileId)
accounts 1—* account_role *—1 roles       (PK tổ hợp + granted_by → accounts)
roles    *—* permissions                  (role_permission)
accounts 1—1 advertiser_profiles 1—* advertisements
accounts 1—* group_members *—1 groups     (PK tổ hợp, role trong nhóm)
accounts 1—* posts *—0..1 groups
posts    1—* post_medias | post_likes | comments
comments 1—* comments                     (parentId, tự tham chiếu)
accounts 1—* friend_ships (sender/receiver, eager)
accounts 1—* messages     (sender/receiver)
accounts 1—* notifications(actor/receptor)
accounts 1—* reports      (reporter, resolved_by)
```

## 4. Điểm KHÔNG map 1:1 được và cách xử lý

| # | Khác biệt | Quyết định |
|---|---|---|
| 1 | `@Column({select:false})` cho `accounts.password` | JPA không có tương đương khai báo. Field vẫn là `String password` bình thường; **không DTO nào chứa nó**, và mọi luồng đọc dùng projection/DTO. Chỉ `AccountRepository.findByUsername/​findByEmail` (luồng auth) mới đọc entity đầy đủ. |
| 2 | Soft delete tự động của TypeORM (`find()` và QueryBuilder tự thêm `deletedAt IS NULL`) | Dùng `@SQLRestriction("deletedAt IS NULL")` trên các entity có cột đó. **Ngoại lệ `Notifications`**: `findById` của TS dùng `withDeleted:true` → entity này **không** gắn `@SQLRestriction`, thay vào đó `findListByAccountId` thêm điều kiện tường minh. Thực tế không luồng nào gọi `softRemove()` nên `deletedAt` luôn NULL — đây là parity về ngữ nghĩa truy vấn, không phải về dữ liệu. |
| 3 | `decimal` → TS ép về `number` bằng transformer | Entity dùng `BigDecimal`; DTO trả `Double` để JSON ra `0` chứ không phải `"0.00"` — khớp response cũ. |
| 4 | `datetime(6)` ↔ `Date` của JS, serialize ISO-8601 UTC (`2026-08-10T05:12:33.000Z`) | Entity dùng `LocalDateTime` (không timezone, khớp cột); DTO/Jackson serialize sang ISO-8601 UTC theo zone hệ thống để chuỗi giống hệt. |
| 5 | `date` (dateOfBirth) trả về chuỗi `YYYY-MM-DD` | `LocalDate` + Jackson JavaTimeModule (`WRITE_DATES_AS_TIMESTAMPS=false`). |
| 6 | `synchronize: true` | Java dùng `spring.jpa.hibernate.ddl-auto=update` ở dev/test và `validate` ở prod. Không thêm migration tool để không đổi schema. |
| 6a | **Naming strategy mặc định của Spring Boot** | `CamelCaseToUnderscoresNamingStrategy` đổi cả tên cột đã khai tường minh (`createdAt` → `created_at`, `receptorId` → `receptor_id`), tạo ra một bộ cột song song và khoá ngoại trỏ vào cột không tồn tại. **Bắt buộc** đặt `spring.jpa.hibernate.naming.physical-strategy=PhysicalNamingStrategyStandardImpl`. Lỗi này đã xảy ra thật khi chạy thử và được phát hiện nhờ so schema với bản TS. |
| 6b | Khoá chính là `int`, không phải `bigint` | `@PrimaryGeneratedColumn()` của TypeORM trên property `number` sinh ra cột `int`. Vì vậy MỌI id trong Java là `Integer` chứ không phải `Long` — dùng `Long` thì FK sinh ra `bigint` và MySQL từ chối ràng buộc với cột `int` đang có. |
| 6c | `@UpdateTimestamp` của Hibernate luôn ép cột NOT NULL | Bỏ annotation đó, gán `updatedAt` bằng `@PrePersist`/`@PreUpdate` trong `BaseEntity` — cho đúng ngữ nghĩa `@UpdateDateColumn` mà vẫn giữ cột nullable như schema cũ. |
| 7 | `ORDER BY RAND()` (MySQL) | Giữ nguyên bằng native/`function('rand')` — kết quả ngẫu nhiên là một phần hành vi (suggest). |
| 8 | `ILike` của TypeORM trên MySQL | TypeORM dịch `ILike` thành `LIKE` (MySQL vốn không phân biệt hoa thường theo collation mặc định `utf8mb4_0900_ai_ci`). Java dùng `LIKE` thẳng — cùng kết quả. |
| 9 | Enum lưu dạng `enum` của MySQL | `@Enumerated(EnumType.STRING)` + `@Column(columnDefinition = "enum(...)")` để `ddl-auto=update` không đổi cột thành varchar. |
| 10 | `groups` là từ khoá MySQL | `@Table(name = "\`groups\`")` (backtick — Hibernate tự dịch sang ký tự quote của dialect). |
| 11 | Cascade của TypeORM (`cascade:true` ở app-level) vs `onDelete` (DB-level) | `onDelete` giữ nguyên bằng `@OnDelete(action=CASCADE/SET_NULL)`; `cascade:true` (post→medias/likes/comments) chuyển thành `cascade = CascadeType.ALL` ở quan hệ tương ứng. |
| 12 | PK tổ hợp `account_role`, `group_members` có kèm quan hệ `@ManyToOne` trùng cột | Dùng `@IdClass` + `@ManyToOne @JoinColumn(insertable=false, updatable=false)` để không xung đột cột. |
| 13 | `accounts.isBanned` là `tinyint` nullable, code so sánh `== 1` | Map thành `Integer` (không phải `boolean`) để giữ được giá trị NULL và ngữ nghĩa so sánh. |
| 14 | `post_likes` không có UNIQUE | **Không** thêm ràng buộc — thêm sẽ đổi lỗi từ 200-idempotent sang 500. |

## 5. Kết quả đối chiếu schema (đã chạy thật)

Cách kiểm: dựng hai database rỗng, cho **backend TypeScript** tự sinh schema vào `lopet_ts_ref`
(`synchronize: true`) và **backend Java** tự sinh vào `lopet_java_ref` (`ddl-auto: update`), rồi so
`information_schema` giữa hai bên.

```
TS: 145 cột   JAVA: 145 cột
diff (tên cột | kiểu | nullable) => SCHEMA IDENTICAL
Khoá ngoại: 27 / 27 trùng nhau, kể cả quy tắc ON DELETE
```

Hai khác biệt còn lại, **chỉ xuất hiện khi tạo schema từ đầu** và không ảnh hưởng hành vi:

| Khác biệt | TS | Java | Ảnh hưởng |
|---|---|---|---|
| Thứ tự cột trong PK của `group_members` | `(group_id, account_id)` | `(account_id, group_id)` | Không. Hibernate sắp cột khoá của `@IdClass` theo thứ tự alphabet và không cấu hình được; chạy trên database sẵn có thì PK cũ được giữ nguyên vì `ddl-auto` không sửa khoá. |
| Index phụ trên `role_permission.role_id` | có index riêng | dùng tiền tố của PK | Không. PK `(role_id, permission_id)` đã phủ đúng cột đó. |

## 6. Rủi ro dữ liệu đã ghi nhận (không sửa trong phạm vi migration)

* `posts.postType` nullable và có thể NULL với dữ liệu cũ → **không dùng nó cho quyết định quyền**.
* `friend_ships` cho phép hai bản ghi ngược chiều giữa cùng một cặp.
* `post_likes` cho phép trùng nếu ghi vòng qua service.
* Xoá tài khoản là **xoá cứng** và kéo theo CASCADE trên posts/comments/reports/group_members.
