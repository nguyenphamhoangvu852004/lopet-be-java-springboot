-- Migration cho refactor module Profile: người dùng không còn tự tạo hồ sơ.
--
-- Bối cảnh: mô hình cũ tạo hồ sơ RỜI (`POST /v1/profiles`) rồi gắn vào tài khoản bằng một request
-- thứ hai (`POST /v1/profiles/{id}`). Bước gắn KHÔNG kiểm tra ownership của profileId, nên bất kỳ
-- ai đăng nhập cũng trỏ được `accounts.profileId` của mình vào hồ sơ của người khác. Hai endpoint
-- đó đã bị xoá; script này dọn hậu quả còn lại trong dữ liệu và khoá trạng thái sai ở tầng DB.
--
-- Sau refactor, mọi tài khoản được cấp hồ sơ ngay lúc tạo (ProfileFactory.seedFor), nên tài khoản
-- có `profileId IS NULL` chỉ còn là dữ liệu cũ — và `PUT /v1/profiles` sẽ trả 404 cho chúng nếu
-- không backfill.
--
-- Chạy: docker exec -i mysql-docker mysql -uroot -pnguyenvu socialmedia < profile-refactor-migration.sql
--
-- THỨ TỰ BẮT BUỘC: bước 5 (UNIQUE) sẽ fail nếu bước 2 chưa tách hết trùng.
-- Sao lưu trước khi chạy: bước 2 và 3 có ghi/xoá dữ liệu.


-- ---------------------------------------------------------------------------
-- Bước 1 — DÒ. Chỉ SELECT, không đụng dữ liệu. Xem kết quả trước khi chạy tiếp.
-- ---------------------------------------------------------------------------

-- 1a. Các profile đang bị NHIỀU tài khoản cùng trỏ tới. Mỗi dòng ở đây là một lần lỗ hổng
--     `setToAccount` đã bị khai thác (hoặc bị gọi nhầm).
SELECT  a.profileId,
        COUNT(*)                      AS soTaiKhoanTro,
        GROUP_CONCAT(a.id ORDER BY a.id)       AS accountIds,
        GROUP_CONCAT(a.username ORDER BY a.id) AS usernames
FROM    accounts a
WHERE   a.profileId IS NOT NULL
GROUP BY a.profileId
HAVING  COUNT(*) > 1;

-- 1b. Hồ sơ mồ côi: không tài khoản nào trỏ tới. Do `POST /v1/profiles` cũ tạo ra rồi bỏ đó.
SELECT  COUNT(*) AS soHoSoMoCoi
FROM    profiles p
WHERE   NOT EXISTS (SELECT 1 FROM accounts a WHERE a.profileId = p.id);

-- 1c. Tài khoản chưa có hồ sơ — sẽ được backfill ở bước 4.
SELECT  COUNT(*) AS soTaiKhoanChuaCoHoSo
FROM    accounts
WHERE   profileId IS NULL;


-- ---------------------------------------------------------------------------
-- Bước 2 — TÁCH TRÙNG. Giữ tài khoản có id NHỎ NHẤT ở lại với hồ sơ gốc; các tài khoản
--          còn lại được cấp hồ sơ mới mang dữ liệu seed (KHÔNG clone dữ liệu của nạn nhân —
--          chúng vốn không có quyền với dữ liệu đó).
-- ---------------------------------------------------------------------------

CREATE TEMPORARY TABLE tmp_ke_chiem_hoso AS
SELECT  a.id AS accountId, a.username
FROM    accounts a
JOIN   (SELECT profileId, MIN(id) AS chuThatSu
        FROM   accounts
        WHERE  profileId IS NOT NULL
        GROUP BY profileId
        HAVING COUNT(*) > 1) d
  ON    a.profileId = d.profileId
 AND    a.id <> d.chuThatSu;

INSERT INTO profiles (fullName, phoneNumber, bio, sex, dateOfBirth, hometown, avatarUrl, coverUrl,
                      createdAt, updatedAt, deletedAt)
SELECT  t.username, '', CONCAT('Xin chào, mình là ', t.username), NULL, NULL, '', '', '',
        NOW(), NOW(), NULL
FROM    tmp_ke_chiem_hoso t;

-- Ghép tài khoản với hồ sơ vừa tạo theo thứ tự id (mỗi username một row mới)
UPDATE  accounts a
JOIN    tmp_ke_chiem_hoso t ON t.accountId = a.id
JOIN   (SELECT p.id AS profileId, p.fullName
        FROM   profiles p
        WHERE  NOT EXISTS (SELECT 1 FROM accounts x WHERE x.profileId = p.id)) moi
  ON    moi.fullName = t.username
SET     a.profileId = moi.profileId;

DROP TEMPORARY TABLE tmp_ke_chiem_hoso;


-- ---------------------------------------------------------------------------
-- Bước 3 — XOÁ HỒ SƠ MỒ CÔI. Chạy SAU bước 2, nếu không sẽ xoá nhầm hồ sơ vừa tạo.
-- ---------------------------------------------------------------------------

DELETE  p
FROM    profiles p
WHERE   NOT EXISTS (SELECT 1 FROM accounts a WHERE a.profileId = p.id);


-- ---------------------------------------------------------------------------
-- Bước 4 — BACKFILL. Mọi tài khoản chưa có hồ sơ được cấp một row seed, giống hệt thứ
--          ProfileFactory.seedFor() sinh ra cho tài khoản mới.
-- ---------------------------------------------------------------------------

INSERT INTO profiles (fullName, phoneNumber, bio, sex, dateOfBirth, hometown, avatarUrl, coverUrl,
                      createdAt, updatedAt, deletedAt)
SELECT  a.username, '', CONCAT('Xin chào, mình là ', a.username), NULL, NULL, '', '', '',
        NOW(), NOW(), NULL
FROM    accounts a
WHERE   a.profileId IS NULL;

UPDATE  accounts a
JOIN   (SELECT p.id AS profileId, p.fullName
        FROM   profiles p
        WHERE  NOT EXISTS (SELECT 1 FROM accounts x WHERE x.profileId = p.id)) moi
  ON    moi.fullName = a.username
SET     a.profileId = moi.profileId
WHERE   a.profileId IS NULL;


-- ---------------------------------------------------------------------------
-- Bước 5 — KHOÁ Ở TẦNG DB. Từ đây trạng thái "hai tài khoản một hồ sơ" không tồn tại được
--          nữa, bất kể tầng ứng dụng có bug gì. MySQL cho phép nhiều NULL trong cột UNIQUE
--          nên ràng buộc này không cản tài khoản chưa backfill.
-- ---------------------------------------------------------------------------

ALTER TABLE accounts ADD CONSTRAINT uq_accounts_profileId UNIQUE (profileId);


-- ---------------------------------------------------------------------------
-- Bước 6 — KIỂM LẠI. Cả ba truy vấn phải trả về 0.
-- ---------------------------------------------------------------------------

SELECT  (SELECT COUNT(*) FROM (SELECT profileId FROM accounts WHERE profileId IS NOT NULL
                               GROUP BY profileId HAVING COUNT(*) > 1) x) AS conTrung,
        (SELECT COUNT(*) FROM profiles p
         WHERE NOT EXISTS (SELECT 1 FROM accounts a WHERE a.profileId = p.id))  AS conMoCoi,
        (SELECT COUNT(*) FROM accounts WHERE profileId IS NULL)                 AS conThieuHoSo;
