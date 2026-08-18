-- Migration cho cơ chế tham gia nhóm: xin vào, duyệt, mời.
--
-- Bối cảnh: `group_members` chỉ có (group_id, pet_id, role, joined_at) và KHÔNG có cột trạng thái.
-- Vì vậy "tồn tại hàng" đồng nghĩa "đang là thành viên", và không có cách nào diễn tả một yêu cầu
-- vào nhóm đang chờ duyệt hay một lời mời chưa được trả lời. Trước bản này cũng không có endpoint
-- nào để tự tham gia hay rời nhóm: đường duy nhất vào nhóm là quản trị nhóm INSERT thẳng một hàng,
-- tức là người "được mời" thành thành viên ngay mà không được hỏi.
--
-- Sau migration:
--   status = 'ACTIVE'                        -> thành viên thật
--   status = 'PENDING', invited_by IS NULL   -> pet tự xin vào nhóm PRIVATE, quản trị nhóm duyệt
--   status = 'PENDING', invited_by NOT NULL  -> được mời, chính pet được mời duyệt
--
-- Chạy: docker exec -i mysql-docker mysql -uroot -pnguyenvu socialmedia < group-membership-status-migration.sql
--
-- CHẠY TRƯỚC khi khởi động phiên bản mới. `ddl-auto: update` tự thêm được hai cột ở bước 1-2 trên
-- dev, nhưng KHÔNG nới được enum ở bước 3 (Hibernate không sửa định nghĩa cột đã tồn tại), còn prod
-- chạy `validate` nên không tự thêm gì cả.
--
-- An toàn để chạy lại: cả ba bước đều được bọc trong kiểm tra information_schema.


-- ---------------------------------------------------------------------------
-- Bước 1 — CỘT TRẠNG THÁI.
--
-- DEFAULT 'ACTIVE' là thứ làm cho migration này không cần backfill: mọi hàng đang có đều là thành
-- viên thật, và đó cũng là giá trị đúng cho chúng.
--
-- Chỉ hai giá trị, không có 'REJECTED': từ chối thì XOÁ hàng, đúng như rời nhóm. Nhờ vậy một pet bị
-- từ chối vẫn xin lại được, và mọi truy vấn chỉ phải phân biệt hai trạng thái.
-- ---------------------------------------------------------------------------

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'group_members' AND column_name = 'status') = 0,
    'ALTER TABLE `group_members`
        ADD COLUMN `status` enum(''PENDING'',''ACTIVE'') NOT NULL DEFAULT ''ACTIVE'' AFTER `role`',
    'SELECT ''Bước 1: cột status đã tồn tại, bỏ qua''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------------
-- Bước 2 — CỘT invited_by: AI MỜI.
--
-- Cột này là thứ phân biệt HAI LOẠI PENDING, nên nó quyết định "ai được quyền duyệt hàng này".
--
-- CỐ Ý KHÔNG ĐẶT KHOÁ NGOẠI, cùng lý do như notifications.objectId. Hai lựa chọn còn lại đều sai:
--   ON DELETE SET NULL  -> xoá pet đã mời sẽ biến một lời mời thành một "yêu cầu xin vào", và quản
--                          trị nhóm tự duyệt được một hàng mà người bị mời chưa hề đồng ý.
--   ON DELETE CASCADE   -> xoá pet đã mời sẽ xoá cả những hàng ĐÃ thành ACTIVE, tức là đá một thành
--                          viên thật ra khỏi nhóm chỉ vì người từng mời họ bị xoá.
-- Đổi lại, giá trị ở đây có thể trỏ tới một pet không còn tồn tại; tầng đọc phải chịu được điều đó.
-- ---------------------------------------------------------------------------

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'group_members' AND column_name = 'invited_by') = 0,
    'ALTER TABLE `group_members` ADD COLUMN `invited_by` int NULL AFTER `status`',
    'SELECT ''Bước 2: cột invited_by đã tồn tại, bỏ qua''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------------
-- Bước 3 — NỚI ENUM notifications.objectType.
--
-- Bốn loại thông báo mới của luồng nhóm. objectId của cả bốn là ID NHÓM.
--
-- Mọi giá trị cũ phải được liệt kê lại: MySQL biến giá trị nằm ngoài enum thành chuỗi rỗng, nên bỏ
-- sót một tên là mất dữ liệu im lặng. 'POST' vẫn còn dù không sinh mới bao giờ.
--
-- Bước này KHÔNG có kiểm tra bọc ngoài vì MODIFY COLUMN là idempotent — chạy lại chỉ ghi lại đúng
-- định nghĩa đó.
-- ---------------------------------------------------------------------------

ALTER TABLE `notifications`
    MODIFY COLUMN `objectType` enum('POST_LIKE','POST_COMMENT','MESSAGE','FRIEND_REQUEST',
                                    'FRIEND_ACCEPTED','POST',
                                    'GROUP_JOIN_REQUESTED','GROUP_JOIN_APPROVED',
                                    'GROUP_INVITED','GROUP_INVITE_ACCEPTED') NOT NULL;


-- ---------------------------------------------------------------------------
-- Kiểm tra sau khi chạy
-- ---------------------------------------------------------------------------

SELECT  status, COUNT(*) AS rows_count
FROM    `group_members`
GROUP BY status;

SELECT  COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM    information_schema.columns
WHERE   table_schema = DATABASE()
  AND   table_name = 'group_members'
  AND   COLUMN_NAME IN ('status', 'invited_by');
