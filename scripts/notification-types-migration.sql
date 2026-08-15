-- Migration cho thông báo bấm được: phân loại đúng + trỏ tới đúng đối tượng.
--
-- Bối cảnh: bảng `notifications` chỉ có `objectType enum('POST','MESSAGE')` và KHÔNG có cột nào
-- lưu id của đối tượng được nói tới. Hệ quả là thông báo chỉ hiển thị được chứ không bấm vào đâu
-- được, và bốn loại sự kiện khác nhau (thích bài, bình luận, mời kết bạn, chấp nhận kết bạn) đều
-- bị dồn chung vào giá trị 'POST'.
--
-- Chạy: docker exec -i mysql-docker mysql -uroot -pnguyenvu socialmedia < notification-types-migration.sql
--
-- An toàn để chạy lại: bước 2 và 3 chỉ chạm vào bản ghi chưa được sửa. Bước 1 sẽ báo lỗi
-- "Duplicate column" ở lần thứ hai — bỏ qua được.


-- ---------------------------------------------------------------------------
-- Bước 1 — NỚI ENUM VÀ THÊM CỘT.
--
-- 'POST' vẫn nằm trong danh sách dù không còn được sinh mới: dữ liệu cũ đang mang giá trị đó, bỏ
-- ra khỏi enum là MySQL biến chúng thành chuỗi rỗng.
--
-- objectId để NULL được, và cố ý không có khoá ngoại: cột này trỏ tới ba bảng khác nhau tuỳ loại
-- (posts, messages, accounts) nên không có một bảng đích cố định để tham chiếu.
-- ---------------------------------------------------------------------------

ALTER TABLE notifications
    MODIFY COLUMN objectType enum('POST_LIKE','POST_COMMENT','MESSAGE','FRIEND_REQUEST',
                                  'FRIEND_ACCEPTED','POST') NOT NULL,
    ADD COLUMN objectId int NULL AFTER objectType;


-- ---------------------------------------------------------------------------
-- Bước 2 — PHÂN LOẠI LẠI DỮ LIỆU CŨ THEO NỘI DUNG.
--
-- Đây là ƯỚC LƯỢNG, không phải sự thật: loại thật đã không được lưu lại. Nhưng câu chữ hồi đó do
-- frontend sinh ra từ vài chuỗi cố định, nên khớp theo nội dung là suy ra được đúng trong hầu hết
-- trường hợp — và một biểu tượng đúng vẫn hơn là tất cả cùng mang một biểu tượng chung chung.
--
-- Những bản ghi không khớp mẫu nào thì giữ nguyên 'POST'. Giao diện hiểu loại đó là "hiện ra
-- nhưng không bấm được", đúng với thực tế là chúng không có objectId.
--
-- objectId vẫn NULL sau bước này: id của bài viết/tin nhắn liên quan chưa từng được ghi ở đâu cả,
-- không có nguồn nào để lấy lại. Thông báo cũ vì vậy vẫn không bấm được — chỉ thông báo sinh ra
-- từ sau bản vá mới có đích đến.
-- ---------------------------------------------------------------------------

UPDATE  notifications
SET     objectType = 'POST_LIKE'
WHERE   objectType = 'POST'
  AND   content LIKE '%thích bài viết%';

UPDATE  notifications
SET     objectType = 'POST_COMMENT'
WHERE   objectType = 'POST'
  AND   content LIKE '%bình luận bài viết%';

UPDATE  notifications
SET     objectType = 'FRIEND_REQUEST'
WHERE   objectType = 'POST'
  AND   content LIKE '%gửi cho bạn lời mời kết bạn%';

UPDATE  notifications
SET     objectType = 'FRIEND_ACCEPTED'
WHERE   objectType = 'POST'
  AND   content LIKE '%chấp nhận lời mời kết bạn%';


-- ---------------------------------------------------------------------------
-- Bước 3 — INDEX cho truy vấn nóng duy nhất của module: danh sách thông báo của một người, mới
--          nhất trước. Không có nó, mỗi lần mở chuông là một lần quét toàn bảng.
-- ---------------------------------------------------------------------------

CREATE INDEX idx_notifications_receptor_created
    ON notifications (receptorId, createdAt);


-- ---------------------------------------------------------------------------
-- Bước 4 — KIỂM LẠI. Cột đầu là số thông báo cũ không suy ra được loại (chấp nhận được, chúng chỉ
--          hiển thị chứ không bấm được). Cột sau phải bằng 0 kể từ lần deploy tiếp theo trở đi:
--          mọi thông báo MỚI đều phải có objectId.
-- ---------------------------------------------------------------------------

SELECT  (SELECT COUNT(*) FROM notifications WHERE objectType = 'POST')      AS conLoaiCu,
        (SELECT COUNT(*) FROM notifications
         WHERE objectType <> 'POST' AND objectId IS NULL)                   AS thieuObjectId;
