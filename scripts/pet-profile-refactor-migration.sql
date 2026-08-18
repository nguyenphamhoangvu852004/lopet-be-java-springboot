-- Migration cho refactor mô hình Profile: Account / AccountProfile / Pet / PetProfile.
--
-- Bối cảnh: mô hình cũ là Account (1-1) Profile (1-N) Pet, trong đó Pet chỉ là bản ghi CRUD và quan
-- hệ sở hữu nằm ở bảng nối `pet_ownerships` (đồng sở hữu PRIMARY_OWNER/CO_OWNER). Mô hình mới lấy
-- PET làm thực thể hoạt động trên mạng xã hội:
--
--     Account (1-1) AccountProfile   -- thông tin CHỦ, không public
--     Account (1-N) Pet              -- sở hữu trực tiếp qua pets.account_id
--     Pet     (1-1) PetProfile       -- mặt công khai: handle, displayName, avatar, bio, privacy
--
-- Dự án KHÔNG dùng Flyway/Liquibase (đã kiểm pom.xml và src/main/resources): schema sinh từ entity
-- qua `spring.jpa.hibernate.ddl-auto: update`, còn các thay đổi mà `update` KHÔNG tự làm được —
-- đổi tên bảng, đổi tên cột, xoá bảng, xoá cột — thì nằm ở script tay trong thư mục này. Đó là lý do
-- file này tồn tại: chạy nó TRƯỚC khi khởi động ứng dụng phiên bản mới.
--
-- Chạy: docker exec -i mysql-docker mysql -uroot -pnguyenvu socialmedia < pet-profile-refactor-migration.sql
--
-- THỨ TỰ BẮT BUỘC: bước 5 phải chạy sau bước 4 (cần pets.account_id đã có dữ liệu trước khi đặt
-- NOT NULL), và bước 6 phải chạy sau bước 5 (pet_profiles tham chiếu pets).
-- Sao lưu trước khi chạy: bước 2, 3, 7 và 8 có xoá/ghi dữ liệu.


-- ---------------------------------------------------------------------------
-- Bước 1 — DÒ. Chỉ SELECT, không đụng dữ liệu. Xem kết quả trước khi chạy tiếp.
-- ---------------------------------------------------------------------------

-- 1a. Thú cưng đang có NHIỀU chủ. Mỗi dòng ở đây là một quan hệ đồng sở hữu sẽ MẤT sau migration:
--     mô hình mới chỉ giữ lại PRIMARY_OWNER. Xem kỹ trước khi chạy bước 4.
SELECT  po.pet_id,
        COUNT(*)                                   AS soChuSoHuu,
        GROUP_CONCAT(po.user_id ORDER BY po.user_id) AS accountIds
FROM    pet_ownerships po
GROUP BY po.pet_id
HAVING  COUNT(*) > 1;

-- 1b. Thú cưng MỒ CÔI: không có bản ghi sở hữu nào. Chúng sẽ bị xoá ở bước 7 vì cột
--     pets.account_id là NOT NULL và không có giá trị nào đúng để điền.
SELECT  COUNT(*) AS soThuCungMoCoi
FROM    pets p
WHERE   NOT EXISTS (SELECT 1 FROM pet_ownerships po WHERE po.pet_id = p.id);

-- 1c. Thú cưng có bản ghi sở hữu nhưng KHÔNG có PRIMARY_OWNER (dữ liệu lỗi). Bước 4 sẽ lấy chủ có
--     id nhỏ nhất cho những con này.
SELECT  COUNT(*) AS soThieuPrimaryOwner
FROM    pets p
WHERE   EXISTS (SELECT 1 FROM pet_ownerships po WHERE po.pet_id = p.id)
  AND   NOT EXISTS (SELECT 1 FROM pet_ownerships po
                    WHERE po.pet_id = p.id AND po.ownership_type = 'PRIMARY_OWNER');


-- ---------------------------------------------------------------------------
-- Bước 2 — ĐỔI TÊN BẢNG HỒ SƠ CHỦ. `profiles` -> `account_profiles`.
--          Tên cũ đã mơ hồ từ lúc `pet_profiles` ra đời.
-- ---------------------------------------------------------------------------

RENAME TABLE profiles TO account_profiles;


-- ---------------------------------------------------------------------------
-- Bước 3 — ĐỔI TÊN CỘT KHOÁ NGOẠI. accounts.profileId -> accounts.account_profile_id.
--
--          Phải DROP khoá ngoại trước khi CHANGE cột: MySQL không cho đổi tên cột đang bị một
--          FOREIGN KEY tham chiếu. Tên constraint do TypeORM/Hibernate sinh tự động nên phải tra
--          bằng truy vấn dưới đây rồi thay vào lệnh DROP — KHÔNG đoán tên.
-- ---------------------------------------------------------------------------

-- Tra tên constraint hiện tại (chạy trước, rồi thay <TEN_FK> ở dòng dưới):
SELECT  CONSTRAINT_NAME
FROM    information_schema.KEY_COLUMN_USAGE
WHERE   TABLE_SCHEMA = DATABASE()
  AND   TABLE_NAME   = 'accounts'
  AND   COLUMN_NAME  = 'profileId'
  AND   REFERENCED_TABLE_NAME IS NOT NULL;

-- ALTER TABLE accounts DROP FOREIGN KEY <TEN_FK>;
-- ALTER TABLE accounts DROP INDEX uq_accounts_profileId;

ALTER TABLE accounts CHANGE COLUMN profileId account_profile_id INT NULL;

ALTER TABLE accounts
    ADD CONSTRAINT uq_accounts_account_profile_id UNIQUE (account_profile_id),
    ADD CONSTRAINT fk_accounts_account_profile
        FOREIGN KEY (account_profile_id) REFERENCES account_profiles (id) ON DELETE CASCADE;


-- ---------------------------------------------------------------------------
-- Bước 4 — CHUYỂN SỞ HỮU TỪ BẢNG NỐI SANG CỘT. pet_ownerships -> pets.account_id.
--
--          Ưu tiên PRIMARY_OWNER; con nào không có thì lấy user_id nhỏ nhất. Quan hệ CO_OWNER bị
--          BỎ — mô hình mới không có đồng sở hữu (xem 1a).
-- ---------------------------------------------------------------------------

ALTER TABLE pets ADD COLUMN account_id INT NULL;

UPDATE  pets p
JOIN   (SELECT  po.pet_id,
                COALESCE(
                    MIN(CASE WHEN po.ownership_type = 'PRIMARY_OWNER' THEN po.user_id END),
                    MIN(po.user_id)
                ) AS ownerId
        FROM    pet_ownerships po
        GROUP BY po.pet_id) chu
  ON    chu.pet_id = p.id
SET     p.account_id = chu.ownerId;


-- ---------------------------------------------------------------------------
-- Bước 5 — DỌN THÚ CƯNG MỒ CÔI rồi KHOÁ CỘT. Chạy SAU bước 4.
--
--          Xoá MỀM chứ không xoá cứng, đúng chính sách của module: có thể đã có bài viết trỏ tới
--          những con này. Nhưng NOT NULL thì vẫn phải thoả, nên hàng nào không có chủ sẽ bị loại
--          hẳn — chấp nhận được vì trạng thái "pet không chủ" vốn không hợp lệ ngay cả ở mô hình cũ.
-- ---------------------------------------------------------------------------

DELETE FROM pets WHERE account_id IS NULL;

ALTER TABLE pets MODIFY COLUMN account_id INT NOT NULL;

ALTER TABLE pets
    ADD CONSTRAINT fk_pets_account
        FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE;

CREATE INDEX idx_pets_account_id ON pets (account_id);

DROP TABLE pet_ownerships;


-- ---------------------------------------------------------------------------
-- Bước 6 — TẠO BẢNG HỒ SƠ CÔNG KHAI.
--
--          `pet_id` UNIQUE là bất biến "một con vật đúng một hồ sơ" ở tầng DB. `handle` UNIQUE là
--          định danh trong URL và trong @mention.
-- ---------------------------------------------------------------------------

CREATE TABLE pet_profiles (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    pet_id        INT          NOT NULL,
    handle        VARCHAR(30)  NOT NULL,
    display_name  VARCHAR(50)  NOT NULL,
    avatar_url    TEXT         NULL,
    cover_url     TEXT         NULL,
    bio           VARCHAR(500) NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    visibility    VARCHAR(20)  NOT NULL DEFAULT 'PUBLIC',
    createdAt     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updatedAt     DATETIME(6)  NULL,
    deletedAt     DATETIME(6)  NULL,
    CONSTRAINT uq_pet_profiles_pet_id UNIQUE (pet_id),
    CONSTRAINT uq_pet_profiles_handle UNIQUE (handle),
    CONSTRAINT fk_pet_profiles_pet FOREIGN KEY (pet_id) REFERENCES pets (id) ON DELETE CASCADE
);


-- ---------------------------------------------------------------------------
-- Bước 7 — BACKFILL HỒ SƠ CÔNG KHAI. Mỗi con vật đang có được cấp một hồ sơ, giống hệt thứ
--          PetProfileFactory.seedFor() sinh ra cho con mới.
--
--          `handle` sinh từ id ('pet_' + id) đúng như PetProfilePolicy.seedHandle — không lấy theo
--          tên vì tên cho phép dấu tiếng Việt, khoảng trắng và trùng lặp, ba thứ handle đều cấm.
--          `bio`/`visibility` chuyển nguyên từ hai cột cùng tên trên `pets` (chúng bị xoá ở bước 8).
-- ---------------------------------------------------------------------------

INSERT INTO pet_profiles (pet_id, handle, display_name, avatar_url, cover_url, bio,
                          status, visibility, createdAt, updatedAt, deletedAt)
SELECT  p.id,
        CONCAT('pet_', p.id),
        p.name,
        '',
        '',
        COALESCE(p.bio, ''),
        CASE WHEN p.deletedAt IS NULL THEN 'ACTIVE' ELSE 'DEACTIVATED' END,
        COALESCE(p.visibility, 'PUBLIC'),
        NOW(6),
        NOW(6),
        p.deletedAt
FROM    pets p;


-- ---------------------------------------------------------------------------
-- Bước 8 — DỌN CỘT ĐÃ CHUYỂN CHỖ trên `pets`, và đổi giá trị enum trạng thái.
--
--          ARCHIVED -> DEACTIVATED: đổi tên cho khớp PetProfileStatus, hai bảng phải nói cùng một
--          từ vựng. Cột là VARCHAR nên không cần ALTER kiểu.
-- ---------------------------------------------------------------------------

UPDATE pets SET status = 'DEACTIVATED' WHERE status = 'ARCHIVED';

ALTER TABLE pets DROP COLUMN bio;
ALTER TABLE pets DROP COLUMN visibility;


-- ---------------------------------------------------------------------------
-- Bước 9 — KIỂM LẠI. Cả năm truy vấn phải trả về 0.
-- ---------------------------------------------------------------------------

SELECT  (SELECT COUNT(*) FROM pets WHERE account_id IS NULL)                       AS petThieuChu,
        (SELECT COUNT(*) FROM pets p
         WHERE NOT EXISTS (SELECT 1 FROM pet_profiles pp WHERE pp.pet_id = p.id))  AS petThieuHoSo,
        (SELECT COUNT(*) FROM pet_profiles pp
         WHERE NOT EXISTS (SELECT 1 FROM pets p WHERE p.id = pp.pet_id))           AS hoSoMoCoi,
        (SELECT COUNT(*) FROM pets WHERE status = 'ARCHIVED')                      AS conTrangThaiCu,
        (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pet_ownerships')        AS conBangNoi;
