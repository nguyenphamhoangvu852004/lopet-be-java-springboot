-- Dọn cột và bảng CŨ mà `spring.jpa.hibernate.ddl-auto: update` không tự bỏ được.
--
-- Bối cảnh: schema của môi trường dev sinh ra từ entity qua `ddl-auto: update`. Chế độ đó chỉ THÊM
-- (bảng, cột, index) và không bao giờ XOÁ — nên sau hai vòng refactor, database còn giữ nguyên cột
-- của mô hình cũ bên cạnh cột của mô hình mới:
--
--     pets.visibility, pets.bio        -- đã chuyển sang bảng pet_profiles
--     posts.account_id                 -- đã chuyển thành posts.pet_id
--     post_likes.account_id            -- đã chuyển thành post_likes.pet_id
--     comments.account_id              -- đã chuyển thành comments.pet_id
--     group_members.account_id + PK (account_id, group_id)
--     bảng pet_ownerships              -- mô hình đồng sở hữu đã bị bỏ
--
-- Cột cũ nào NOT NULL và không có DEFAULT thì làm hỏng mọi INSERT của entity mới, vì entity không
-- còn khai nó nên Hibernate không đưa vào câu lệnh:
--
--     Field 'visibility' doesn't have a default value
--
-- KHÁC GÌ social-content-pet-fk-migration.sql: script kia dành cho database THẬT còn dữ liệu cũ —
-- nó tự THÊM cột pet_id rồi backfill từ account_id. Ở đây `ddl-auto: update` đã tạo sẵn cột pet_id
-- và cả khoá ngoại của chúng, nên chỉ còn phần XOÁ. Chạy nhầm script kia vào database này sẽ hỏng
-- ngay ở bước `ADD COLUMN pet_id` vì cột đã tồn tại.
--
-- Chạy: docker exec -i lopet-mysql-docker mysql -uroot -pnguyenvu socialmedia < drop-legacy-account-columns.sql
--
-- AN TOÀN DỮ LIỆU: script này KHÔNG di trú gì cả, nó chỉ xoá. Chỉ chạy khi các bảng liên quan còn
-- rỗng (môi trường dev vừa dựng lại), hoặc sau khi đã chạy xong script di trú tương ứng. Bước 0
-- dưới đây đếm số hàng để kiểm tra trước.


-- ---------------------------------------------------------------------------
-- Bước 0 — DÒ. Bốn số này phải bằng 0, nếu không thì DỪNG và chạy
--          social-content-pet-fk-migration.sql (hoặc pet-profile-refactor-migration.sql) trước.
-- ---------------------------------------------------------------------------

SELECT  (SELECT COUNT(*) FROM posts)         AS soBaiViet,
        (SELECT COUNT(*) FROM post_likes)    AS soLuotThich,
        (SELECT COUNT(*) FROM comments)      AS soBinhLuan,
        (SELECT COUNT(*) FROM group_members) AS soThanhVienNhom,
        (SELECT COUNT(*) FROM pets)          AS soThuCung;


-- ---------------------------------------------------------------------------
-- Bước 1 — CỘT CỦA HỒ SƠ THÚ CƯNG trên bảng `pets`.
--
--          Đây chính là cột gây lỗi "Field 'visibility' doesn't have a default value": nó NOT NULL,
--          không DEFAULT, và entity Pet đã không còn khai nó từ lúc PetProfile ra đời.
-- ---------------------------------------------------------------------------

ALTER TABLE pets DROP COLUMN visibility;
ALTER TABLE pets DROP COLUMN bio;


-- ---------------------------------------------------------------------------
-- Bước 2 — KHOÁ NGOẠI account_id trên bốn bảng nội dung.
--
--          Tên constraint do Hibernate sinh (FKxxxxxxxxxxxxxxxxxxxx) nên khác nhau giữa các máy —
--          phải tra information_schema rồi dựng câu lệnh động. MySQL không có
--          `DROP FOREIGN KEY IF EXISTS`, nên nhánh "không tìm thấy" trả về một SELECT vô hại.
-- ---------------------------------------------------------------------------

SET @fk := (SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'posts'
              AND COLUMN_NAME = 'account_id' AND REFERENCED_TABLE_NAME IS NOT NULL LIMIT 1);
SET @sql := IF(@fk IS NULL, 'SELECT ''posts: khong con FK account_id''',
                CONCAT('ALTER TABLE posts DROP FOREIGN KEY `', @fk, '`'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk := (SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'post_likes'
              AND COLUMN_NAME = 'account_id' AND REFERENCED_TABLE_NAME IS NOT NULL LIMIT 1);
SET @sql := IF(@fk IS NULL, 'SELECT ''post_likes: khong con FK account_id''',
                CONCAT('ALTER TABLE post_likes DROP FOREIGN KEY `', @fk, '`'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk := (SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'comments'
              AND COLUMN_NAME = 'account_id' AND REFERENCED_TABLE_NAME IS NOT NULL LIMIT 1);
SET @sql := IF(@fk IS NULL, 'SELECT ''comments: khong con FK account_id''',
                CONCAT('ALTER TABLE comments DROP FOREIGN KEY `', @fk, '`'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk := (SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'group_members'
              AND COLUMN_NAME = 'account_id' AND REFERENCED_TABLE_NAME IS NOT NULL LIMIT 1);
SET @sql := IF(@fk IS NULL, 'SELECT ''group_members: khong con FK account_id''',
                CONCAT('ALTER TABLE group_members DROP FOREIGN KEY `', @fk, '`'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------------
-- Bước 3 — KHOÁ CHÍNH của group_members: (account_id, group_id) -> (group_id, pet_id).
--
--          `ddl-auto: update` KHÔNG bao giờ đổi khoá chính, nên nó vẫn là cặp cũ dù @IdClass đã đổi
--          — Hibernate sẽ đọc/ghi theo cặp mới trong khi DB chống trùng theo cặp cũ.
--
--          Một lệnh ALTER duy nhất, không tách làm hai: giữa hai lệnh riêng lẻ bảng sẽ có một
--          khoảnh khắc không có khoá chính nào, và nếu lệnh thứ hai hỏng thì bảng nối mất luôn
--          ràng buộc chống trùng.
-- ---------------------------------------------------------------------------

ALTER TABLE group_members
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (group_id, pet_id);


-- ---------------------------------------------------------------------------
-- Bước 4 — CỘT account_id. Phải sau bước 2 và 3 (cột đang là một nửa của PK cũ).
--
--          comments.account_id và group_members.account_id là NOT NULL không DEFAULT — chúng là
--          hai quả mìn tiếp theo sau pets.visibility, phát nổ ở lần bình luận và lần vào nhóm đầu
--          tiên. Xoá luôn ở đây thay vì chờ gặp lỗi.
-- ---------------------------------------------------------------------------

ALTER TABLE posts         DROP COLUMN account_id;
ALTER TABLE post_likes    DROP COLUMN account_id;
ALTER TABLE comments      DROP COLUMN account_id;
ALTER TABLE group_members DROP COLUMN account_id;


-- ---------------------------------------------------------------------------
-- Bước 5 — BẢNG NỐI ĐỒNG SỞ HỮU đã bị bỏ. Quyền sở hữu nay là 1-N trực tiếp qua pets.account_id;
--          bảng này không còn entity nào ánh xạ tới nên chỉ nằm đó gây nhầm khi đọc schema.
-- ---------------------------------------------------------------------------

DROP TABLE IF EXISTS pet_ownerships;


-- ---------------------------------------------------------------------------
-- Bước 6 — KIỂM LẠI. Sáu số đầu phải bằng 0; hai dòng cuối là khoá chính mới, đúng thứ tự.
-- ---------------------------------------------------------------------------

SELECT  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'pets'          AND COLUMN_NAME = 'visibility')  AS petsVisibility,
        (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'pets'          AND COLUMN_NAME = 'bio')         AS petsBio,
        (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'posts'         AND COLUMN_NAME = 'account_id')  AS postsAccountId,
        (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'post_likes'    AND COLUMN_NAME = 'account_id')  AS likesAccountId,
        (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'comments'      AND COLUMN_NAME = 'account_id')  AS commentsAccountId,
        (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'group_members' AND COLUMN_NAME = 'account_id')  AS membersAccountId;

SELECT  COLUMN_NAME, ORDINAL_POSITION
FROM    information_schema.KEY_COLUMN_USAGE
WHERE   TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'group_members'
  AND   CONSTRAINT_NAME = 'PRIMARY'
ORDER BY ORDINAL_POSITION;
