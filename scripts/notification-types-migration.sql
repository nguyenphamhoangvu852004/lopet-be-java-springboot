


ALTER TABLE notifications
    MODIFY COLUMN objectType enum('POST_LIKE','POST_COMMENT','MESSAGE','FRIEND_REQUEST',
                                  'FRIEND_ACCEPTED','POST') NOT NULL,
    ADD COLUMN objectId int NULL AFTER objectType;



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



CREATE INDEX idx_notifications_receptor_created
    ON notifications (receptorId, createdAt);



SELECT  (SELECT COUNT(*) FROM notifications WHERE objectType = 'POST')      AS conLoaiCu,
        (SELECT COUNT(*) FROM notifications
         WHERE objectType <> 'POST' AND objectId IS NULL)                   AS thieuObjectId;
