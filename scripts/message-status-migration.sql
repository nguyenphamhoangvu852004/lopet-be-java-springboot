-- Migration cho trạng thái tin nhắn realtime: "đã gửi / đã nhận / đã xem".
--
-- Bối cảnh: bảng `messages` đã có cột `status` enum('SENT','DELIVERED','READ') nhưng chỉ giữ được
-- trạng thái MỚI NHẤT — không biết tin được nhận lúc nào, xem lúc nào. Script này thêm hai mốc
-- thời gian và một index cho hai truy vấn nóng mới:
--   * đánh dấu đã xem cả hội thoại  (receiverId, senderId, status)
--   * đếm tin chưa đọc của một người (receiverId, status)
--
-- Chạy: docker exec -i mysql-docker mysql -uroot -pnguyenvu socialmedia < message-status-migration.sql
--
-- An toàn để chạy lại: bước 2 và 3 chỉ backfill dữ liệu cũ, không ghi đè giá trị đã có.
-- Bước 1 và 4 sẽ báo lỗi "Duplicate column"/"Duplicate key" nếu chạy lần thứ hai — bỏ qua được.


-- ---------------------------------------------------------------------------
-- Bước 1 — THÊM CỘT. Cả hai đều NULL được: NULL nghĩa là "chưa xảy ra".
-- ---------------------------------------------------------------------------

ALTER TABLE messages
    ADD COLUMN deliveredAt datetime(6) NULL AFTER status,
    ADD COLUMN readAt      datetime(6) NULL AFTER deliveredAt;


-- ---------------------------------------------------------------------------
-- Bước 2 — BACKFILL DỮ LIỆU CŨ. Tin nhắn có sẵn không có mốc thật để lấy, nên dùng `updatedAt`
--          (lần cuối cột status bị đổi) làm xấp xỉ. Đây là ước lượng, KHÔNG phải sự thật —
--          nhưng để NULL thì giao diện hiện "chưa nhận" cho tin đã đọc từ lâu, sai nặng hơn.
-- ---------------------------------------------------------------------------

UPDATE  messages
SET     deliveredAt = COALESCE(deliveredAt, updatedAt, createdAt)
WHERE   status IN ('DELIVERED', 'READ')
  AND   deliveredAt IS NULL;

UPDATE  messages
SET     readAt = COALESCE(readAt, updatedAt, createdAt)
WHERE   status = 'READ'
  AND   readAt IS NULL;


-- ---------------------------------------------------------------------------
-- Bước 3 — DỌN TRẠNG THÁI KHÔNG NHẤT QUÁN. Trước bản vá, `PATCH /v1/messages/status/{id}` nhận
--          bất kỳ giá trị nào từ CẢ HAI phía và không chặn lùi trạng thái, nên có thể tồn tại
--          tin READ nhưng thiếu mốc nhận. Từ đây READ luôn kéo theo deliveredAt.
-- ---------------------------------------------------------------------------

UPDATE  messages
SET     deliveredAt = readAt
WHERE   readAt IS NOT NULL
  AND   deliveredAt IS NULL;


-- ---------------------------------------------------------------------------
-- Bước 4 — INDEX. Không có nó, "đánh dấu đã xem cả hội thoại" và badge đếm tin chưa đọc đều
--          quét toàn bảng — hai thao tác chạy mỗi lần người dùng mở một cuộc trò chuyện.
-- ---------------------------------------------------------------------------

CREATE INDEX idx_messages_receiver_sender_status
    ON messages (receiver_id, sender_id, status);


-- ---------------------------------------------------------------------------
-- Bước 5 — KIỂM LẠI. Cả ba truy vấn phải trả về 0.
-- ---------------------------------------------------------------------------

SELECT  (SELECT COUNT(*) FROM messages
         WHERE status IN ('DELIVERED','READ') AND deliveredAt IS NULL) AS thieuMocNhan,
        (SELECT COUNT(*) FROM messages
         WHERE status = 'READ' AND readAt IS NULL)                     AS thieuMocXem,
        (SELECT COUNT(*) FROM messages
         WHERE readAt IS NOT NULL AND deliveredAt IS NULL)             AS xemMaChuaNhan;
