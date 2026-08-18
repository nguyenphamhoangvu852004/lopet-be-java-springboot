-- Di trú hồ sơ chủ tài khoản: bảng `profiles` (cũ) -> `account_profiles` (mới).
--
-- Bối cảnh: refactor tách AccountProfile khỏi PetProfile đổi tên bảng `profiles` thành
-- `account_profiles` và đổi cột `accounts.profileId` thành `accounts.account_profile_id`. Bước đó
-- nằm ở pet-profile-refactor-migration.sql và CHƯA từng chạy trên database này.
--
-- Thay vào đó `spring.jpa.hibernate.ddl-auto: update` đã tự tạo bảng `account_profiles` RỖNG và cột
-- `account_profile_id` toàn NULL, đặt cạnh bảng/cột cũ vẫn còn nguyên dữ liệu. Kết quả là hồ sơ vẫn
-- nằm trong DB nhưng ứng dụng không thấy:
--
--     Tài khoản chưa có hồ sơ — cần chạy backfill trong scripts/pet-profile-refactor-migration.sql
--
-- VÌ SAO KHÔNG CHẠY ĐƯỢC pet-profile-refactor-migration.sql: bước 2 của script đó là
-- `RENAME TABLE profiles TO account_profiles`, và lệnh đó vỡ ngay vì bảng đích đã tồn tại. Script
-- này là bản thay thế cho đúng tình huống "ddl-auto đã dựng sẵn cấu trúc mới, chỉ thiếu dữ liệu".
--
-- Chạy: docker exec -i lopet-mysql-docker mysql -uroot -pnguyenvu socialmedia < backfill-account-profiles.sql
--
-- SAO LƯU TRƯỚC KHI CHẠY: bước 4 xoá cột và bảng cũ, không quay lui được.


-- ---------------------------------------------------------------------------
-- Bước 1 — DÒ. Chỉ SELECT. `soHoSoMoi` phải bằng 0 (nếu khác 0 thì đã có ai đó
--          chạy backfill hoặc đã tạo hồ sơ mới — DỪNG LẠI và đối chiếu tay,
--          bước 2 dưới đây giữ nguyên id nên sẽ đụng khoá chính).
-- ---------------------------------------------------------------------------

SELECT  (SELECT COUNT(*) FROM accounts)                                AS soTaiKhoan,
        (SELECT COUNT(*) FROM profiles)                                AS soHoSoCu,
        (SELECT COUNT(*) FROM account_profiles)                        AS soHoSoMoi,
        (SELECT COUNT(*) FROM accounts WHERE profileId IS NOT NULL)    AS coTroToiHoSoCu,
        (SELECT COUNT(*) FROM accounts WHERE account_profile_id IS NOT NULL) AS coTroToiHoSoMoi;

-- Tài khoản KHÔNG có hồ sơ ở cả hai bảng. Chúng vẫn sẽ lỗi sau khi chạy script này — hồ sơ nay
-- được cấp tự động lúc đăng ký (AccountProfileFactory), nên đây chỉ có thể là tài khoản tạo từ
-- trước refactor. Bước 5 liệt kê lại chúng kèm cách xử lý tay.
SELECT  a.id, a.username
FROM    accounts a
WHERE   a.profileId IS NULL AND a.account_profile_id IS NULL;


-- ---------------------------------------------------------------------------
-- Bước 2 — CHÉP DỮ LIỆU, GIỮ NGUYÊN `id`.
--
--          Giữ id chứ không để auto_increment cấp mới: cột accounts.profileId đang trỏ theo id đó,
--          nên bước 3 mới copy thẳng được giá trị sang. Chỉ an toàn khi bảng đích còn rỗng — chính
--          là điều bước 1 kiểm.
--
--          Hai bảng có cột giống hệt nhau (ddl-auto sinh account_profiles từ cùng một entity), nên
--          liệt kê tường minh từng cột thay vì SELECT *: thứ tự cột không phải thứ đáng để tin.
-- ---------------------------------------------------------------------------

INSERT INTO account_profiles
        (id, createdAt, updatedAt, deletedAt, fullName, phoneNumber, bio, sex, dateOfBirth,
         hometown, avatarUrl, coverUrl)
SELECT   id, createdAt, updatedAt, deletedAt, fullName, phoneNumber, bio, sex, dateOfBirth,
         hometown, avatarUrl, coverUrl
FROM     profiles;


-- ---------------------------------------------------------------------------
-- Bước 3 — NỐI LẠI TÀI KHOẢN với hồ sơ vừa chép.
-- ---------------------------------------------------------------------------

UPDATE  accounts
SET     account_profile_id = profileId
WHERE   profileId IS NOT NULL AND account_profile_id IS NULL;


-- ---------------------------------------------------------------------------
-- Bước 4 — BỎ CỘT VÀ BẢNG CŨ.
--
--          Phải xoá khoá ngoại trước khi xoá cột, và xoá cột trước khi xoá bảng. Tên constraint do
--          Hibernate sinh nên phải tra information_schema rồi dựng câu lệnh động — MySQL không có
--          `DROP FOREIGN KEY IF EXISTS`.
--
--          Để nguyên bảng `profiles` thì lần sau đọc schema sẽ thấy hai bảng hồ sơ song song và
--          không biết bảng nào là thật; cột `profileId` còn đó cũng vậy.
-- ---------------------------------------------------------------------------

SET @fk := (SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'accounts'
              AND COLUMN_NAME = 'profileId' AND REFERENCED_TABLE_NAME IS NOT NULL LIMIT 1);
SET @sql := IF(@fk IS NULL, 'SELECT ''accounts: khong con FK profileId''',
                CONCAT('ALTER TABLE accounts DROP FOREIGN KEY `', @fk, '`'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE accounts DROP COLUMN profileId;
DROP TABLE IF EXISTS profiles;


-- ---------------------------------------------------------------------------
-- Bước 5 — BÁO CÁO tài khoản vẫn chưa có hồ sơ (kết quả thứ hai của bước 1).
--
--          Cố ý KHÔNG tự cấp hồ sơ ở đây. Ánh xạ 1-1 giữa tài khoản và hồ sơ cần một id sinh ra
--          cho từng hàng, mà MySQL thuần chỉ làm được bằng cách dựa vào việc auto_increment cấp id
--          liên tiếp rồi gán ngược theo thứ tự — một giả định về nội bộ InnoDB, sai âm thầm khi
--          innodb_autoinc_lock_mode đổi hoặc khi có phiên khác chèn xen vào.
--
--          Bất biến "mỗi tài khoản đúng một hồ sơ" do AccountProfileFactory giữ cho tài khoản tạo
--          MỚI. Dòng nào hiện ra ở đây là tài khoản có từ trước refactor và chưa từng có hồ sơ —
--          hiếm, và đáng để xử lý tay:
--
--              INSERT INTO account_profiles (createdAt, updatedAt) VALUES (NOW(6), NOW(6));
--              UPDATE accounts SET account_profile_id = LAST_INSERT_ID() WHERE id = <accountId>;
-- ---------------------------------------------------------------------------

SELECT  a.id AS accountIdConThieuHoSo, a.username
FROM    accounts a
WHERE   a.account_profile_id IS NULL;


-- ---------------------------------------------------------------------------
-- Bước 6 — KIỂM LẠI. Hai số đầu phải bằng 0. `taiKhoanThieuHoSo` phải bằng số dòng mà bước 5 in
--          ra — khác đi nghĩa là bước 3 nối thiếu.
-- ---------------------------------------------------------------------------

SELECT  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'accounts' AND COLUMN_NAME = 'profileId')     AS conCotProfileIdCu,
        (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'profiles')                                   AS conBangProfilesCu,
        (SELECT COUNT(*) FROM accounts WHERE account_profile_id IS NULL) AS taiKhoanThieuHoSo,
        (SELECT COUNT(*) FROM accounts)                                 AS soTaiKhoan,
        (SELECT COUNT(*) FROM account_profiles)                         AS soHoSoMoi;
