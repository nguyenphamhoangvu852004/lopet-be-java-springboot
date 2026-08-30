


ALTER TABLE messages
    ADD COLUMN deliveredAt datetime(6) NULL AFTER status,
    ADD COLUMN readAt      datetime(6) NULL AFTER deliveredAt;



UPDATE  messages
SET     deliveredAt = COALESCE(deliveredAt, updatedAt, createdAt)
WHERE   status IN ('DELIVERED', 'READ')
  AND   deliveredAt IS NULL;

UPDATE  messages
SET     readAt = COALESCE(readAt, updatedAt, createdAt)
WHERE   status = 'READ'
  AND   readAt IS NULL;



UPDATE  messages
SET     deliveredAt = readAt
WHERE   readAt IS NOT NULL
  AND   deliveredAt IS NULL;



CREATE INDEX idx_messages_receiver_sender_status
    ON messages (receiver_id, sender_id, status);



SELECT  (SELECT COUNT(*) FROM messages
         WHERE status IN ('DELIVERED','READ') AND deliveredAt IS NULL) AS thieuMocNhan,
        (SELECT COUNT(*) FROM messages
         WHERE status = 'READ' AND readAt IS NULL)                     AS thieuMocXem,
        (SELECT COUNT(*) FROM messages
         WHERE readAt IS NOT NULL AND deliveredAt IS NULL)             AS xemMaChuaNhan;
