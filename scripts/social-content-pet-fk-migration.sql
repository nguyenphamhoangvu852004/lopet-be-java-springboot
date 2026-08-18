-- Migration cho việc đổi CHỦ THỂ của nội dung xã hội: tài khoản -> thú cưng.
--
-- Bối cảnh: sau refactor Account/AccountProfile/Pet/PetProfile (xem pet-profile-refactor-migration.sql),
-- PET là thực thể hoạt động trên mạng xã hội, nhưng bốn bảng nội dung vẫn trỏ khoá ngoại về
-- `accounts`. Script này chuyển nốt:
--
--     posts.account_id          -> posts.pet_id
--     post_likes.account_id     -> post_likes.pet_id
--     comments.account_id       -> comments.pet_id
--     group_members PK (group_id, account_id) -> (group_id, pet_id)
--
-- Vì sao trỏ vào `pets.id` chứ không phải `pet_profiles.id`: hồ sơ công khai là thứ đổi được và
-- khoá được (đổi handle, ẩn hồ sơ, DEACTIVATED), còn khoá ngoại phải trỏ vào một danh tính bất
-- biến. Trỏ vào pet_profiles thì mỗi lần kiểm duyệt khoá một hồ sơ là kéo theo câu hỏi phải làm gì
-- với hàng nghìn bài viết đang tham chiếu nó.
--
-- Dự án KHÔNG dùng Flyway/Liquibase: schema sinh từ entity qua `spring.jpa.hibernate.ddl-auto:
-- update`, còn những thay đổi mà `update` KHÔNG tự làm được — đổi tên cột, xoá cột, đổi khoá chính,
-- và mọi việc DI TRÚ DỮ LIỆU — thì nằm ở script tay trong thư mục này.
--
-- CHẠY SCRIPT NÀY TRƯỚC KHI KHỞI ĐỘNG ỨNG DỤNG PHIÊN BẢN MỚI. Nếu chạy ứng dụng trước, Hibernate
-- sẽ tự thêm cột pet_id RỖNG rồi mọi truy vấn đọc feed trả về trống, còn luồng ghi thì đâm vào ràng
-- buộc NOT NULL của comments.pet_id.
--
-- Chạy: docker exec -i mysql-docker mysql -uroot -pnguyenvu socialmedia < social-content-pet-fk-migration.sql
--
-- THỨ TỰ BẮT BUỘC: 2 -> 3 -> 4 -> 5 -> 6 -> 7. Bước 4 xoá dữ liệu không quy được về pet nào, bước 5
-- xoá cột cũ nên không quay lui được sau đó.
-- SAO LƯU TRƯỚC KHI CHẠY: bước 4 và bước 5 đều mất dữ liệu.
--
-- QUY TẮC QUY ĐỔI account -> pet, dùng thống nhất ở mọi bảng: lấy thú cưng CÒN HOẠT ĐỘNG có id nhỏ
-- nhất của tài khoản đó (con đầu tiên người dùng tạo); nếu tài khoản không còn con nào hoạt động thì
-- lấy con có id nhỏ nhất kể cả đã ngừng hoạt động. Đây là phép đoán — dữ liệu cũ KHÔNG chứa thông
-- tin "bài này do con nào đăng", nên không có cách nào đúng hơn. Người dùng nhiều thú cưng sẽ thấy
-- nội dung cũ dồn hết về con đầu tiên.


-- ---------------------------------------------------------------------------
-- Bước 1 — DÒ. Chỉ SELECT, không đụng dữ liệu. Xem kết quả trước khi chạy tiếp.
-- ---------------------------------------------------------------------------

-- 1a. Tài khoản đang có nội dung xã hội nhưng KHÔNG sở hữu thú cưng nào. Toàn bộ bình luận, lượt
--     thích và tư cách thành viên nhóm của những tài khoản này sẽ bị XOÁ ở bước 4; bài viết của họ
--     thì giữ lại với pet_id = NULL (cột nullable) và sẽ chỉ còn tác giả đọc được.
SELECT  a.id                AS accountId,
        a.username,
        (SELECT COUNT(*) FROM posts         t WHERE t.account_id = a.id) AS soBaiViet,
        (SELECT COUNT(*) FROM post_likes    t WHERE t.account_id = a.id) AS soLuotThich,
        (SELECT COUNT(*) FROM comments      t WHERE t.account_id = a.id) AS soBinhLuan,
        (SELECT COUNT(*) FROM group_members t WHERE t.account_id = a.id) AS soThamGiaNhom
FROM    accounts a
WHERE   NOT EXISTS (SELECT 1 FROM pets p WHERE p.account_id = a.id)
  AND   ( EXISTS (SELECT 1 FROM posts         t WHERE t.account_id = a.id)
       OR EXISTS (SELECT 1 FROM post_likes    t WHERE t.account_id = a.id)
       OR EXISTS (SELECT 1 FROM comments      t WHERE t.account_id = a.id)
       OR EXISTS (SELECT 1 FROM group_members t WHERE t.account_id = a.id) );

-- 1b. Tài khoản có NHIỀU thú cưng. Mỗi dòng ở đây là một lần phép đoán ở trên có thể gán sai con:
--     nội dung cũ sẽ dồn hết về con có id nhỏ nhất.
SELECT  p.account_id                              AS accountId,
        COUNT(*)                                  AS soThuCung,
        MIN(p.id)                                 AS petSeNhanNoiDungCu,
        GROUP_CONCAT(p.id ORDER BY p.id)          AS tatCaPetIds
FROM    pets p
WHERE   p.deletedAt IS NULL
GROUP BY p.account_id
HAVING  COUNT(*) > 1;

-- 1c. Nội dung MỒ CÔI sẵn từ trước: account_id trỏ tới tài khoản không còn tồn tại. Bước 4 xoá
--     chúng cùng lượt.
SELECT  (SELECT COUNT(*) FROM posts         t WHERE t.account_id IS NOT NULL
         AND NOT EXISTS (SELECT 1 FROM accounts a WHERE a.id = t.account_id)) AS baiVietMoCoi,
        (SELECT COUNT(*) FROM post_likes    t WHERE t.account_id IS NOT NULL
         AND NOT EXISTS (SELECT 1 FROM accounts a WHERE a.id = t.account_id)) AS luotThichMoCoi,
        (SELECT COUNT(*) FROM comments      t WHERE t.account_id IS NOT NULL
         AND NOT EXISTS (SELECT 1 FROM accounts a WHERE a.id = t.account_id)) AS binhLuanMoCoi,
        (SELECT COUNT(*) FROM group_members t
         WHERE NOT EXISTS (SELECT 1 FROM accounts a WHERE a.id = t.account_id)) AS thanhVienMoCoi;


-- ---------------------------------------------------------------------------
-- Bước 2 — THÊM CỘT pet_id. Cả bốn cột đều tạm thời NULL được, kể cả comments.pet_id và
--          group_members.pet_id (hai cột sẽ thành NOT NULL ở bước 5): không có cách nào điền giá
--          trị ngay lúc ALTER, và đặt NOT NULL trước khi backfill sẽ khiến MySQL nhét số 0 vào mọi
--          hàng — một khoá ngoại trỏ vào pet không tồn tại.
--
--          Cột phải khớp KIỂU của pets.id (int) để tạo được khoá ngoại ở bước 6.
-- ---------------------------------------------------------------------------

ALTER TABLE posts         ADD COLUMN pet_id int NULL AFTER id;
ALTER TABLE post_likes    ADD COLUMN pet_id int NULL AFTER post_id;
ALTER TABLE comments      ADD COLUMN pet_id int NULL AFTER post_id;
ALTER TABLE group_members ADD COLUMN pet_id int NULL AFTER group_id;


-- ---------------------------------------------------------------------------
-- Bước 3 — BACKFILL theo quy tắc đã nêu ở đầu file.
--
--          Hai nhánh COALESCE, không gộp làm một: nhánh đầu ưu tiên thú cưng CÒN HOẠT ĐỘNG, nhánh
--          sau mới chấp nhận con đã ngừng hoạt động. Gộp thành một `MIN(id)` duy nhất sẽ gán nội
--          dung cho một con đã bị ẩn trong khi tài khoản vẫn còn con đang hoạt động — và vì
--          @SQLRestriction lọc pet đã xoá mềm khỏi mọi truy vấn, nội dung đó biến mất khỏi giao
--          diện mà không báo lỗi ở đâu cả.
-- ---------------------------------------------------------------------------

UPDATE posts t
SET    t.pet_id = COALESCE(
           (SELECT MIN(p.id) FROM pets p WHERE p.account_id = t.account_id AND p.deletedAt IS NULL),
           (SELECT MIN(p.id) FROM pets p WHERE p.account_id = t.account_id))
WHERE  t.account_id IS NOT NULL;

UPDATE post_likes t
SET    t.pet_id = COALESCE(
           (SELECT MIN(p.id) FROM pets p WHERE p.account_id = t.account_id AND p.deletedAt IS NULL),
           (SELECT MIN(p.id) FROM pets p WHERE p.account_id = t.account_id))
WHERE  t.account_id IS NOT NULL;

UPDATE comments t
SET    t.pet_id = COALESCE(
           (SELECT MIN(p.id) FROM pets p WHERE p.account_id = t.account_id AND p.deletedAt IS NULL),
           (SELECT MIN(p.id) FROM pets p WHERE p.account_id = t.account_id))
WHERE  t.account_id IS NOT NULL;

UPDATE group_members t
SET    t.pet_id = COALESCE(
           (SELECT MIN(p.id) FROM pets p WHERE p.account_id = t.account_id AND p.deletedAt IS NULL),
           (SELECT MIN(p.id) FROM pets p WHERE p.account_id = t.account_id))
WHERE  t.account_id IS NOT NULL;


-- ---------------------------------------------------------------------------
-- Bước 4 — XOÁ dữ liệu không quy được về pet nào.
--
--          posts được GIỮ LẠI với pet_id = NULL: cột nullable, entity chấp nhận, và mất một bài
--          viết là mất nội dung người dùng viết ra — cái giá cao hơn hẳn một hàng feed hiển thị
--          thiếu tác giả. Bù lại chúng chỉ còn hiện với chủ tài khoản (nhánh E của PostVisibility
--          so theo ap.account.id, mà ap là NULL) nên không rò rỉ ra ngoài.
--
--          Ba bảng còn lại thì KHÔNG giữ được: comments.pet_id là NOT NULL, còn group_members.pet_id
--          nằm trong khoá chính nên cũng không nhận NULL. Một lượt thích hay một tư cách thành viên
--          không có chủ thể thì cũng không mang thông tin gì.
-- ---------------------------------------------------------------------------

DELETE FROM comments      WHERE pet_id IS NULL;
DELETE FROM post_likes    WHERE pet_id IS NULL;
DELETE FROM group_members WHERE pet_id IS NULL;


-- ---------------------------------------------------------------------------
-- Bước 5 — GỠ KHOÁ NGOẠI CŨ VÀ CỘT account_id.
--
--          Tên constraint do Hibernate sinh (FKxxxxxxxxxxxxxxxxxxxx) nên khác nhau giữa các máy —
--          phải tra information_schema rồi dựng câu lệnh động, không hardcode được. MySQL không có
--          `DROP FOREIGN KEY IF EXISTS`, nên nhánh "không tìm thấy" phải trả về một câu SELECT vô
--          hại thay vì bỏ trống.
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

-- Khoá chính ghép phải đổi TRƯỚC khi xoá cột account_id (cột đang là một nửa của PK).
-- Một lệnh ALTER duy nhất, không tách làm hai: giữa hai lệnh riêng lẻ bảng sẽ có một khoảnh khắc
-- không có khoá chính nào, và nếu lệnh thứ hai hỏng thì bảng nối mất luôn ràng buộc chống trùng.
ALTER TABLE group_members
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (group_id, pet_id);

ALTER TABLE posts         DROP COLUMN account_id;
ALTER TABLE post_likes    DROP COLUMN account_id;
ALTER TABLE comments      DROP COLUMN account_id;
ALTER TABLE group_members DROP COLUMN account_id;


-- ---------------------------------------------------------------------------
-- Bước 6 — SIẾT RÀNG BUỘC MỚI.
--
--          ON DELETE CASCADE khớp với @OnDelete(action = CASCADE) trên entity: xoá cứng một con vật
--          kéo theo nội dung của nó. Xoá MỀM (deletedAt) thì không đụng tới đây — đó là việc của
--          @SQLRestriction ở tầng ứng dụng.
--
--          KHÔNG thêm UNIQUE(post_id, pet_id) cho post_likes: bảng vốn không có ràng buộc đó, và
--          thêm vào sẽ đổi hành vi thích trùng từ "200 kèm message" (idempotent, do PostService
--          quyết) thành lỗi ràng buộc 500.
-- ---------------------------------------------------------------------------

ALTER TABLE comments      MODIFY COLUMN pet_id int NOT NULL;
ALTER TABLE group_members MODIFY COLUMN pet_id int NOT NULL;

ALTER TABLE posts
    ADD CONSTRAINT fk_posts_pet         FOREIGN KEY (pet_id) REFERENCES pets (id) ON DELETE CASCADE;
ALTER TABLE post_likes
    ADD CONSTRAINT fk_post_likes_pet    FOREIGN KEY (pet_id) REFERENCES pets (id) ON DELETE CASCADE;
ALTER TABLE comments
    ADD CONSTRAINT fk_comments_pet      FOREIGN KEY (pet_id) REFERENCES pets (id) ON DELETE CASCADE;
ALTER TABLE group_members
    ADD CONSTRAINT fk_group_members_pet FOREIGN KEY (pet_id) REFERENCES pets (id) ON DELETE CASCADE;


-- ---------------------------------------------------------------------------
-- Bước 7 — KIỂM LẠI. Bảy cột đầu phải bằng 0; cột cuối là số bài viết mất tác giả ở bước 4, đối
--          chiếu với kết quả 1a để chắc rằng không có gì ngoài dự kiến.
-- ---------------------------------------------------------------------------

SELECT  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'posts'         AND COLUMN_NAME = 'account_id')  AS conCotCu_posts,
        (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'post_likes'    AND COLUMN_NAME = 'account_id')  AS conCotCu_postLikes,
        (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'comments'      AND COLUMN_NAME = 'account_id')  AS conCotCu_comments,
        (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'group_members' AND COLUMN_NAME = 'account_id')  AS conCotCu_groupMembers,
        (SELECT COUNT(*) FROM comments      WHERE pet_id IS NULL)          AS binhLuanThieuPet,
        (SELECT COUNT(*) FROM group_members WHERE pet_id IS NULL)          AS thanhVienThieuPet,
        (SELECT COUNT(*) FROM post_likes    WHERE pet_id IS NULL)          AS luotThichThieuPet,
        (SELECT COUNT(*) FROM posts         WHERE pet_id IS NULL)          AS baiVietKhongCoTacGia;

-- Khoá chính của group_members phải đúng là (group_id, pet_id) — hai dòng, đúng thứ tự.
SELECT  COLUMN_NAME, ORDINAL_POSITION
FROM    information_schema.KEY_COLUMN_USAGE
WHERE   TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'group_members'
  AND   CONSTRAINT_NAME = 'PRIMARY'
ORDER BY ORDINAL_POSITION;
